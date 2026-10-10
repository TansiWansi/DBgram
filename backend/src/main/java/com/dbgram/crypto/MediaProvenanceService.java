package com.dbgram.crypto;

import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Local, reproducible integrity proofs. These are not blockchain attestations. */
@Service
public class MediaProvenanceService {
    public static final int LEAF_BYTES = 1024 * 1024;
    public String computeSha256(byte[] bytes) { return hex(digest().digest(bytes)); }
    public Manifest inspect(Path file) throws IOException {
        MessageDigest whole = digest();
        List<String> leaves = new ArrayList<>();
        try (InputStream input = Files.newInputStream(file)) {
            byte[] block;
            while ((block = input.readNBytes(LEAF_BYTES)).length > 0) {
                whole.update(block);
                leaves.add(computeSha256(block));
            }
        }
        if (leaves.isEmpty()) leaves.add(computeSha256(new byte[0]));
        return new Manifest(hex(whole.digest()), merkleRoot(leaves), List.copyOf(leaves));
    }
    public String merkleRoot(List<String> hashes) {
        if (hashes.isEmpty()) throw new IllegalArgumentException("At least one leaf is required");
        List<String> level = new ArrayList<>(hashes);
        while (level.size() > 1) level = parentLevel(level);
        return level.getFirst();
    }
    public List<Map<String,String>> proof(List<String> hashes, int leaf) {
        if (leaf < 0 || leaf >= hashes.size()) throw new IllegalArgumentException("Invalid leaf index");
        List<Map<String,String>> proof = new ArrayList<>();
        List<String> level = new ArrayList<>(hashes);
        int index = leaf;
        while (level.size() > 1) {
            int sibling = index % 2 == 0 ? Math.min(index + 1, level.size()-1) : index-1;
            proof.add(Map.of("side", index % 2 == 0 ? "right" : "left", "hash", level.get(sibling)));
            index /= 2;
            level = parentLevel(level);
        }
        return proof;
    }
    private List<String> parentLevel(List<String> level) {
        List<String> next = new ArrayList<>();
        for (int i=0;i<level.size();i+=2) {
            byte[] left = HexFormat.of().parseHex(level.get(i));
            byte[] right = HexFormat.of().parseHex(level.get(Math.min(i+1,level.size()-1)));
            MessageDigest digest = digest(); digest.update(left); digest.update(right);
            next.add(hex(digest.digest()));
        }
        return next;
    }
    public boolean verify(String leafHash,String root,List<Map<String,String>> siblings) {
        if(leafHash==null||root==null||!leafHash.matches("[0-9a-f]{64}")||!root.matches("[0-9a-f]{64}")||siblings==null||siblings.size()>32)return false;
        String current=leafHash;
        for(Map<String,String> sibling:siblings){
            if(sibling==null)return false;
            String side=sibling.get("side"),hash=sibling.get("hash");
            if(side==null||!Set.of("left","right").contains(side)||hash==null||!hash.matches("[0-9a-f]{64}"))return false;
            MessageDigest digest=digest();
            digest.update(HexFormat.of().parseHex(side.equals("left")?hash:current));
            digest.update(HexFormat.of().parseHex(side.equals("left")?current:hash));
            current=hex(digest.digest());
        }
        return MessageDigest.isEqual(current.getBytes(java.nio.charset.StandardCharsets.US_ASCII),root.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }
    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    public record Manifest(String sha256, String merkleRoot, List<String> leaves) {}
}
