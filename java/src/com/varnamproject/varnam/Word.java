package com.varnamproject.varnam;

import java.util.Arrays;
import java.util.List;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;

/**
 *
 */
public final class Word  {

  private _Word word;

  public final class _Word extends Structure {
    public Pointer text;
    public int confidence;
    
    public _Word(Pointer p) {
      super(p);
      read();
    }

    protected List<String> getFieldOrder() {
          return Arrays.asList("text", "confidence");
      }
  }
  
  public String getText() {
    return word.text.getString(0);
  }
  
  public int getConfidence() {
    return word.confidence;
  }
  
  public Word(Pointer p) {
    word = new _Word(p);
  }
} 
