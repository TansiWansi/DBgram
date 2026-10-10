package com.dbgram.worker;

import com.dbgram.service.MediaUploadService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Persistent, restartable FFmpeg work queue; no broker is required for a small deployment. */
@Service @ConditionalOnProperty(name="media.transcode-enabled",havingValue="true")
public class TranscodingWorker {
    private final JdbcTemplate jdbc;
    private final MediaUploadService uploads;
    private final String ffmpeg;
    public TranscodingWorker(JdbcTemplate jdbc,MediaUploadService uploads,@Value("${media.ffmpeg-path:ffmpeg}") String ffmpeg){this.jdbc=jdbc;this.uploads=uploads;this.ffmpeg=ffmpeg;}
    @Scheduled(fixedDelayString="${media.worker-delay-ms:10000}",initialDelay=10000)
    public void processQueue(){
        // Claims abandoned by a crashed worker become eligible again after a bounded lease.
        jdbc.update("update media_uploads set transcode_status='QUEUED' where transcode_status='PROCESSING' and transcode_started_at<now()-interval '30 minutes'");
        List<UUID> claimed=jdbc.queryForList("""
            update media_uploads set transcode_status='PROCESSING',transcode_started_at=now() where id in
            (select id from media_uploads where status='READY' and transcode_status='QUEUED' order by created_at limit 1 for update skip locked)
            returning id
            """,UUID.class);
        for(UUID id:claimed)process(id);
    }
    private void process(UUID id){
        try{
            Path dir=uploads.variantDirectory(id);Files.createDirectories(dir);
            String input=uploads.file(id).toString();
            for(int height:new int[]{360,720}){
                String name="video-"+height;
                run(List.of(ffmpeg,"-nostdin","-y","-protocol_whitelist","file,pipe","-i",input,"-map","0:v:0","-map","0:a:0?",
                    "-vf","scale=-2:'min("+height+",ih)'","-c:v","libx264","-preset","veryfast","-crf","23","-pix_fmt","yuv420p",
                    "-c:a","aac","-b:a","128k","-f","hls","-hls_time","6","-hls_playlist_type","vod",
                    "-hls_segment_filename",dir.resolve(name+"-%04d.ts").toString(),dir.resolve(name+".m3u8").toString()),dir.resolve(name+".log"));
            }
            Files.writeString(dir.resolve("master.m3u8"),"#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-STREAM-INF:BANDWIDTH=900000\nvideo-360.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=2500000\nvideo-720.m3u8\n");
            run(List.of(ffmpeg,"-nostdin","-y","-protocol_whitelist","file,pipe","-i",input,"-map","0:v:0","-map","0:v:0","-map","0:a:0?",
                "-filter:v:0","scale=-2:'min(360,ih)'","-filter:v:1","scale=-2:'min(720,ih)'","-c:v","libx264","-preset","veryfast",
                "-b:v:0","800k","-b:v:1","2400k","-c:a","aac","-f","dash","-seg_duration","6",dir.resolve("manifest.mpd").toString()),dir.resolve("dash.log"));
            jdbc.update("update media_uploads set transcode_status='READY',transcode_error=null where id=? and status='READY'",id);
            jdbc.update("update media_assets set variants=?::jsonb where id=?","{\"hls\":\"/api/media/"+id+"/variants/master.m3u8\",\"dash\":\"/api/media/"+id+"/variants/manifest.mpd\"}",id);
        }catch(Exception ex){
            jdbc.update("update media_uploads set transcode_status='FAILED',transcode_error=? where id=?","Transcoding failed; inspect worker logs and retry",id);
        }
    }
    private void run(List<String> command,Path log) throws Exception {
        Process process=new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if(!process.waitFor(Duration.ofMinutes(8).toMillis(),TimeUnit.MILLISECONDS)){process.destroyForcibly();throw new IllegalStateException("FFmpeg timed out");}
        if(process.exitValue()!=0)throw new IllegalStateException("FFmpeg exited "+process.exitValue());
    }
}
