package com.kapil.typeahead.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RedisNode {

    private String name;
    private String host;
    private int port;
}
