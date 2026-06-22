package com.kapil.typeahead.cache;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

/**
 * Immutable representation of a Redis node in the cluster.
 * Used as a HashMap key in redisTemplatesMap — equality is based on name only.
 */
@Getter
@RequiredArgsConstructor
@EqualsAndHashCode(of = "name")
@ToString
public class RedisNode {

    private final String name;
    private final String host;
    private final int port;
}
