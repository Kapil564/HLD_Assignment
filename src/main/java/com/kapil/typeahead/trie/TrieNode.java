package com.kapil.typeahead.trie;

import java.util.HashMap;
import java.util.Map;

public class TrieNode {

    private final Map<Character, TrieNode> children = new HashMap<>();

    private boolean endOfWord = false;

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