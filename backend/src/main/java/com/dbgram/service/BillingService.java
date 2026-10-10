package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.*;
import com.stripe.exception.*;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.*;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class BillingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final String apiKey,webhookSecret,priceId,publicUrl;
    public BillingService(JdbcTemplate jdbc,ObjectMapper json,
            @Value("${stripe.api-key:}") String apiKey,@Value("${stripe.webhook-secret:}") String webhookSecret,
            @Value("${stripe.price-id:}") String priceId,@Value("${app.public-url:}") String publicUrl) {
        this.jdbc=jdbc;this.json=json;this.apiKey=apiKey;this.webhookSecret=webhookSecret;this.priceId=priceId;
        this.publicUrl=publicUrl==null?"":publicUrl.trim().replaceAll("/$","");
    }
    private boolean keyPresent(String key){return key!=null&&!key.isBlank()&&!key.contains("CHANGE_ME");}
    private boolean publicUrlPresent(){
        try {
            java.net.URI uri=java.net.URI.create(publicUrl);
            return Set.of("http","https").contains(uri.getScheme()) && uri.getHost()!=null
                && uri.getUserInfo()==null && uri.getRawQuery()==null && uri.getFragment()==null
                && (uri.getPath()==null || uri.getPath().isEmpty());
        } catch(RuntimeException ex){return false;}
    }
    public boolean configured(){return keyPresent(apiKey)&&keyPresent(webhookSecret)&&keyPresent(priceId)&&publicUrlPresent();}
    public Map<String,Object> status(UUID user){
        List<Map<String,Object>> subscriptions=jdbc.queryForList("select subscription_id,community_id,status,current_period_end from billing_entitlements where user_id=? order by updated_at desc",user);
        boolean active=subscriptions.stream().anyMatch(s->Set.of("active","trialing").contains(String.valueOf(s.get("status")))
            && (s.get("current_period_end")==null||((java.util.Date)s.get("current_period_end")).toInstant().isAfter(Instant.now())));
        return Map.of("configured",configured(),"active",active,"subscriptions",subscriptions,"checkoutAvailable",configured());
    }
    public Map<String,String> checkout(UUID user,UUID community) {
        if(!configured())throw unavailable("Payments are not configured on this server");
        String selectedPrice=priceId;
        if(community!=null){
            List<Map<String,Object>> rows=jdbc.queryForList("select settings->>'stripePriceId' as price from communities where id=? and is_paid=true and (expires_at is null or expires_at>now())",community);
            if(rows.isEmpty())throw AppException.notFound("Paid community",community);
            Object price=rows.getFirst().get("price");
            if(price==null||!price.toString().startsWith("price_"))throw unavailable("This community has no configured Stripe price");
            selectedPrice=price.toString();
        }
        String email=jdbc.queryForObject("select email from users where id=?",String.class,user);
        var subscription=SessionCreateParams.SubscriptionData.builder().putMetadata("userId",user.toString());
        if(community!=null)subscription.putMetadata("communityId",community.toString());
        var params=SessionCreateParams.builder().setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setClientReferenceId(user.toString()).setCustomerEmail(email)
            .setSuccessUrl(publicUrl+"/profile?checkout=success").setCancelUrl(publicUrl+"/profile?checkout=canceled")
            .setSubscriptionData(subscription.build())
            .addLineItem(SessionCreateParams.LineItem.builder().setPrice(selectedPrice).setQuantity(1L).build()).build();
        try{
            Session session=Session.create(params,RequestOptions.builder().setApiKey(apiKey).build());
            return Map.of("url",session.getUrl(),"sessionId",session.getId());
        }catch(StripeException ex){throw unavailable("Payment provider is temporarily unavailable");}
    }
    public Map<String,String> portal(UUID user){
        if(!configured())throw unavailable("Payments are not configured on this server");
        List<String> customers=jdbc.queryForList("select customer_id from billing_entitlements where user_id=? and customer_id is not null order by updated_at desc limit 1",String.class,user);
        if(customers.isEmpty())throw AppException.badRequest("No billing account yet");
        try{
            var params=com.stripe.param.billingportal.SessionCreateParams.builder().setCustomer(customers.getFirst()).setReturnUrl(publicUrl+"/profile").build();
            var session=com.stripe.model.billingportal.Session.create(params,RequestOptions.builder().setApiKey(apiKey).build());
            return Map.of("url",session.getUrl());
        }catch(StripeException ex){throw unavailable("Billing portal is temporarily unavailable");}
    }

    /** The unique event insert and entitlement mutation commit together, including concurrent deliveries. */
    @Transactional
    public boolean webhook(String body,String signature){
        if(!keyPresent(webhookSecret))throw unavailable("Stripe webhook is not configured");
        if(body.length()>1024*1024)throw AppException.badRequest("Webhook payload is too large");
        Event event;
        try{event=Webhook.constructEvent(body,signature,webhookSecret);}
        catch(SignatureVerificationException|RuntimeException ex){throw AppException.badRequest("Invalid Stripe signature or payload");}
        int inserted=jdbc.update("insert into stripe_events(id,event_type,event_created) values (?,?,?) on conflict(id) do nothing",event.getId(),event.getType(),event.getCreated());
        if(inserted==0)return false;
        if(!Set.of("customer.subscription.created","customer.subscription.updated","customer.subscription.deleted").contains(event.getType()))return true;
        try{
            JsonNode subscription=json.readTree(body).path("data").path("object");
            String subscriptionId=subscription.path("id").asText();
            if(subscriptionId.isBlank())throw AppException.badRequest("Missing subscription id");
            List<Map<String,Object>> existing=jdbc.queryForList("select * from billing_entitlements where subscription_id=?",subscriptionId);
            String owner=subscription.path("metadata").path("userId").asText();
            if(owner.isBlank()&&!existing.isEmpty())owner=String.valueOf(existing.getFirst().get("user_id"));
            // Events for other products on the same Stripe account are acknowledged but never grant access.
            if(owner.isBlank())return true;
            UUID user=UUID.fromString(owner);
            if(jdbc.queryForObject("select count(*) from users where id=?",Long.class,user)==0)return true;
            jdbc.queryForObject("select id from users where id=? for update",UUID.class,user);
            String communityText=subscription.path("metadata").path("communityId").asText();
            UUID community=communityText.isBlank()?null:UUID.fromString(communityText);
            String state=event.getType().endsWith(".deleted")?"canceled":subscription.path("status").asText("incomplete");
            if(!Set.of("active","trialing","past_due","canceled","incomplete","incomplete_expired","unpaid","paused").contains(state))state="incomplete";
            long end=subscription.path("current_period_end").asLong(0);
            if(end==0)end=subscription.path("items").path("data").path(0).path("current_period_end").asLong(0);
            Timestamp until=end==0?null:Timestamp.from(Instant.ofEpochSecond(end));
            String customer=subscription.path("customer").isTextual()?subscription.path("customer").asText():subscription.path("customer").path("id").asText();
            int changed=jdbc.update("""
                insert into billing_entitlements(subscription_id,user_id,community_id,customer_id,status,current_period_end,last_event_created)
                values(?,?,?,?,?,?,?) on conflict(subscription_id) do update set
                status=excluded.status,current_period_end=excluded.current_period_end,customer_id=excluded.customer_id,
                last_event_created=excluded.last_event_created,updated_at=now()
                where billing_entitlements.last_event_created < excluded.last_event_created
                or (billing_entitlements.last_event_created = excluded.last_event_created and excluded.status not in ('active','trialing'))
                """,subscriptionId,user,community,customer,state,until,event.getCreated());
            if(changed>0&&community!=null)syncCommunity(user,community,subscriptionId,state,until);
            return true;
        }catch(AppException ex){throw ex;}
        catch(Exception ex){throw AppException.badRequest("Invalid subscription metadata");}
    }
    private void syncCommunity(UUID user,UUID community,String subscription,String state,Timestamp until){
        Long exists=jdbc.queryForObject("select count(*) from communities where id=? and is_paid=true",Long.class,community);
        if(exists==null||exists==0)return;
        String mapped=state.equals("active")?"ACTIVE":state.equals("trialing")?"TRIALING":state.equals("past_due")?"PAST_DUE":"CANCELED";
        if(Set.of("ACTIVE","TRIALING").contains(mapped)) {
            int joined=jdbc.update("""
                insert into community_members(id,community_id,user_id,role,subscription_status,stripe_subscription_id,subscription_expires_at,joined_at)
                values(?,?,?,'MEMBER',?,?,?,now()) on conflict(community_id,user_id) do nothing
                """,UUID.randomUUID(),community,user,mapped,subscription,until);
            if(joined>0)jdbc.update("update communities set member_count=member_count+1 where id=?",community);
        }
        jdbc.update("update community_members set subscription_status=?,stripe_subscription_id=?,subscription_expires_at=? where community_id=? and user_id=?",mapped,subscription,until,community,user);
    }
    private AppException unavailable(String text){return new AppException(HttpStatus.SERVICE_UNAVAILABLE,"BILLING_UNAVAILABLE",text);}
}

