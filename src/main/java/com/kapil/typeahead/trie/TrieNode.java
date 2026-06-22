package com.kapil.typeahead.trie;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class TrieNode {

    private final Map<Character, TrieNode> children = new ConcurrentHashMap<>();

    private volatile boolean endOfWord = false;

    public Map<Character, TrieNode> getChildren() {
        return children;
    }

    public boolean isEndOfWord() {
        return endOfWord;
    }

    public void setEndOfWord(boolean endOfWord) {
        this.endOfWord = endOfWord;
    }
}