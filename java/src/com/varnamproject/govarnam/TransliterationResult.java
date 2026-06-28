package com.varnamproject.govarnam;

import java.util.Arrays;
import java.util.List;

public class TransliterationResult {
    public Suggestion[] ExactMatches;
    public Suggestion[] DictionarySuggestions;
    public Suggestion[] PatternDictionarySuggestions;
    public Suggestion[] TokenizerSuggestions;
    public Suggestion[] GreedyTokenized;

    public TransliterationResult(Suggestion[] e, Suggestion[] d, Suggestion[] p, Suggestion[] t, Suggestion[] g) {
        this.ExactMatches = e;
        this.DictionarySuggestions = d;
        this.PatternDictionarySuggestions = p;
        this.TokenizerSuggestions = t;
        this.GreedyTokenized = g;
    }
}
