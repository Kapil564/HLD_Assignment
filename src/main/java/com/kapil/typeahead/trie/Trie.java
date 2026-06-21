package com.kapil.typeahead.trie;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class Trie {

    private final TrieNode root = new TrieNode();

    public void insert(String word) {

        TrieNode current = root;

        for (char c : word.toCharArray()) {

            current = current.getChildren()
                    .computeIfAbsent(c, k -> new TrieNode());
        }

        current.setEndOfWord(true);
    }

    public List<String> search(String prefix, int limit) {

        TrieNode current = root;

        for (char c : prefix.toCharArray()) {

            current = current.getChildren().get(c);

            if (current == null) {
                return List.of();
            }
        }

        List<String> results = new ArrayList<>();

        dfs(current, prefix, results, limit);

        return results;
    }

    private void dfs(
            TrieNode node,
            String currentWord,
            List<String> results,
            int limit
    ) {

        if (results.size() >= limit) {
            return;
        }

        if (node.isEndOfWord()) {
            results.add(currentWord);
        }

        for (var entry : node.getChildren().entrySet()) {

            dfs(
                    entry.getValue(),
                    currentWord + entry.getKey(),
                    results,
                    limit
            );
        }
    }
}