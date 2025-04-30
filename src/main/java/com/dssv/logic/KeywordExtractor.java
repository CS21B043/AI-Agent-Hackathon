package com.dssv.logic;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

public class KeywordExtractor {

    /**
     * Extract the top 'limit' keywords from the given text using Lucene's EnglishAnalyzer.
     */
    public static List<String> topKeywords(String text, int limit) {
        Map<String, Integer> freq = new HashMap<>();
        EnglishAnalyzer analyzer = new EnglishAnalyzer(); // default stopword set

        try (TokenStream ts = analyzer.tokenStream("field", new StringReader(text))) {
            CharTermAttribute termAttr = ts.addAttribute(CharTermAttribute.class);
            ts.reset();
            while (ts.incrementToken()) {
                String term = termAttr.toString();
                freq.put(term, freq.getOrDefault(term, 0) + 1);
            }
            ts.end();
        } catch (IOException e) {
            // Won't happen on StringReader
        } finally {
            analyzer.close();
        }

        // Sort by frequency desc, then alphabetically, then take top 'limit'
        return freq.entrySet()
                   .stream()
                   .sorted(Comparator
                       .<Map.Entry<String,Integer>>comparingInt(Map.Entry::getValue)
                       .reversed()
                       .thenComparing(Map.Entry::getKey))
                   .limit(limit)
                   .map(Map.Entry::getKey)
                   .collect(Collectors.toList());
    }
}
