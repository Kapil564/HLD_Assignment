package com.kapil.typeahead.cache;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ConsistentHashingService {

    private final TreeMap<Long, RedisNode> ring = new TreeMap<>();
    private final int virtualNodes = 150;

    private final Map<RedisNode, StringRedisTemplate> redisTemplatesMap;

    @PostConstruct
    public void init() {
        for (RedisNode node : redisTemplatesMap.keySet()) {
            addNode(node);
        }
        System.out.println("Consistent Hashing Service initialized with " + redisTemplatesMap.size() + " Redis nodes.");
    }

    public void addNode(RedisNode node) {

        for (int i = 0; i < virtualNodes; i++) {

            String virtualNodeName = node.getName() + ":" + i;
            long hash = hash(virtualNodeName);
            ring.put(hash, node);
        }
    }

    public void removeNode(RedisNode node) {

        for (int i = 0; i < virtualNodes; i++) {

            String virtualNodeName = node.getName() + ":" + i;
            long hash = hash(virtualNodeName);
            ring.remove(hash);
        }
    }

    public RedisNode getNode(String key) {

        if (ring.isEmpty()) {
            return null;
        }

        long hash = hash(key);

        Map.Entry<Long, RedisNode> entry = ring.ceilingEntry(hash);

        if (entry == null) {
            entry = ring.firstEntry();
        }

        return entry.getValue();
    }

    public StringRedisTemplate getTemplate(String key) {
        RedisNode node = getNode(key);
        return node != null ? redisTemplatesMap.get(node) : null;
    }

    private long hash(String key) {

        try {

            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(key.getBytes());

            long hash = ((long) (digest[3] & 0xFF) << 24)
                    | ((long) (digest[2] & 0xFF) << 16)
                    | ((long) (digest[1] & 0xFF) << 8)
                    | (digest[0] & 0xFF);

            return hash;

        } catch (NoSuchAlgorithmException e) {

            throw new RuntimeException("MD5 algorithm not found", e);
        }
    }

    public int getRingSize() {
        return ring.size();
    }
}
