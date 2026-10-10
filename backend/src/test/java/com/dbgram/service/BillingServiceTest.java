package com.dbgram.service;

import com.dbgram.util.AppException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class BillingServiceTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final String secret="whsec_local_test_secret";
    private final BillingService service=new BillingService(jdbc,new ObjectMapper(),"",secret,"","http://localhost:8080");
    @Test void invalidAndExpiredSignaturesNeverMutateBilling() throws Exception {
        String body=event(Instant.now().getEpochSecond());
        assertThrows(AppException.class,()->service.webhook(body,"t=1,v1=bad"));
        assertThrows(AppException.class,()->service.webhook(body,signature(body,Instant.now().minusSeconds(600).getEpochSecond())));
        verifyNoInteractions(jdbc);
    }
    @Test void repeatedValidEventIsAcknowledgedWithoutApplyingTwice()throws Exception {
        long now=Instant.now().getEpochSecond();String body=event(now);
        when(jdbc.update(anyString(),any(Object[].class))).thenReturn(1,0);
        assertTrue(service.webhook(body,signature(body,now)));
        assertFalse(service.webhook(body,signature(body,now)));
        // Unmapped subscriptions belong to another product and never create an entitlement.
        verify(jdbc,times(2)).update(startsWith("insert into stripe_events"),eq("evt_test"),eq("customer.subscription.deleted"),eq(now));
    }
    @Test void missingProviderConfigDisablesCheckout(){assertFalse(service.configured());assertThrows(AppException.class,()->service.checkout(java.util.UUID.randomUUID(),null));verifyNoInteractions(jdbc);}
    @Test void checkoutNeedsTheConfiguredPublicDomain(){
        for(String origin:new String[]{"", "https://user:password@dbgram.test", "https://dbgram.test?redirect=other"}) {
            var invalid=new BillingService(jdbc,new ObjectMapper(),"sk_test",secret,"price_test",origin);
            assertFalse(invalid.configured());
            assertThrows(AppException.class,()->invalid.checkout(java.util.UUID.randomUUID(),null));
        }
        assertTrue(new BillingService(jdbc,new ObjectMapper(),"sk_test",secret,"price_test","https://dbgram.test/").configured());
        verifyNoInteractions(jdbc);
    }
    private String event(long now){return "{\"id\":\"evt_test\",\"object\":\"event\",\"api_version\":\"2024-06-20\",\"created\":"+now+",\"type\":\"customer.subscription.deleted\",\"data\":{\"object\":{\"id\":\"sub_test\",\"object\":\"subscription\",\"metadata\":{}}}}";}
    private String signature(String body,long timestamp)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal((timestamp+"."+body).getBytes(StandardCharsets.UTF_8)));}
}

