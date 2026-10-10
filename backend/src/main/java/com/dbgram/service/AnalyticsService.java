package com.dbgram.service;

import com.dbgram.util.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class AnalyticsService {
    private final JdbcTemplate jdbc;
    private final SocialService social;
    @Transactional
    public Map<String,Object> recordView(UUID post,UUID viewer){
        Boolean allowed=jdbc.queryForObject("select check_post_access(?,?)",Boolean.class,post,viewer);
        if(!Boolean.TRUE.equals(allowed))throw AppException.notFound("Post",post);
        int added=jdbc.update("insert into post_view_events(post_id,viewer_id) values(?,?) on conflict do nothing",post,viewer);
        social.engage(viewer,post,"view",true);
        return Map.of("recorded",added>0);
    }
    @Transactional(readOnly=true)
    public Map<String,Object> getInsights(UUID userId){
        Map<String,Object> result=new LinkedHashMap<>(jdbc.queryForMap("""
            select count(*) as posts,coalesce(sum(p.view_count),0) as views,coalesce(sum(p.like_count),0) as likes,
            coalesce(sum(p.comment_count),0) as comments,coalesce(sum(p.save_count),0) as saves
            from posts p where p.status='PUBLISHED' and exists(select 1 from post_authors a where a.post_id=p.id and a.user_id=?)
            """,userId));
        Map<String,Object> profile=jdbc.queryForMap("select follower_count as followers,following_count as following from users where id=?",userId);
        result.putAll(profile);
        result.put("uniqueViewers",jdbc.queryForObject("""
            select count(distinct v.viewer_id) from post_view_events v
            where exists(select 1 from post_authors a where a.post_id=v.post_id and a.user_id=?)
            """,Long.class,userId));
        double views=((Number)result.get("views")).doubleValue();
        double interactions=((Number)result.get("likes")).doubleValue()+((Number)result.get("comments")).doubleValue()+((Number)result.get("saves")).doubleValue();
        result.put("engagementRate",views==0?0:Math.round(interactions/views*10000d)/100d);
        result.put("daily",jdbc.queryForList("""
            select to_char(days.d,'YYYY-MM-DD') as date,
              (select count(*) from posts p join post_authors a on a.post_id=p.id where a.user_id=? and p.created_at::date=days.d::date) as posts,
              (select count(*) from post_view_events v join post_authors a on a.post_id=v.post_id where a.user_id=? and v.viewed_on=days.d::date) as views,
              (select count(distinct v.viewer_id) from post_view_events v join post_authors a on a.post_id=v.post_id where a.user_id=? and v.viewed_on=days.d::date) as unique_viewers
            from generate_series(current_date-29,current_date,'1 day'::interval) days(d) order by days.d
            """,userId,userId,userId));
        result.put("topPosts",jdbc.queryForList("""
            select p.id,p.text_content,p.view_count,p.like_count,p.comment_count,p.save_count,p.published_at
            from posts p where p.status='PUBLISHED' and exists(select 1 from post_authors a where a.post_id=p.id and a.user_id=?)
            order by p.view_count desc,p.published_at desc limit 10
            """,userId));
        result.put("viewDefinition","Views count unique signed-in viewers per post; daily views count unique viewers per post and UTC date.");
        return result;
    }
}
