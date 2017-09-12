/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rnabloom.io;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import rnabloom.io.FastqPairReader.FastqReadPair;
import static rnabloom.util.SeqUtils.filterFastq;
import static rnabloom.util.SeqUtils.reverseComplement;

/**
 *
 * @author kmnip
 */
public class FastqPairReader implements Iterator<FastqReadPair> {
    private final FastqReader leftReader;
    private final FastqReader rightReader;
    private final Pattern qualPattern;
    private final Supplier<FastqReadPair> nextFunction;
                
    public FastqPairReader(FastqReader leftReader, FastqReader rightReader, Pattern qualPattern, boolean revCompLeft, boolean revCompRight) {
        this.qualPattern = qualPattern;
        this.leftReader = leftReader;
        this.rightReader = rightReader;
        if (revCompLeft) { 
            if (revCompRight) {
                nextFunction = this::nextRR;
            }
            else {
                nextFunction = this::nextRF;
            }
        }
        else {
            if (revCompRight) {
                nextFunction = this::nextFR;
            }
            else {
                nextFunction = this::nextFF;
            }                
        }
    }
    
    public final class FastqReadPair {
        public ArrayList<String> left; // left segments
        public ArrayList<String> right; // right segments
//        public int originalLeftLength;
//        public int originalRightLength;
//        public int numLeftBasesTrimmed;
//        public int numRightBasesTrimmed;
    }

    @Override
    public boolean hasNext() {
        return this.leftReader.hasNext() && this.rightReader.hasNext();
    }

    @Override
    public FastqReadPair next() {
        return nextFunction.get();
    }

    private FastqReadPair nextFF() {
        FastqReadPair p = new FastqReadPair();

        FastqRecord frLeft = leftReader.next();
        FastqRecord frRight = rightReader.next();

        if (!frLeft.name.equals(frRight.name)) {
            throw new NoSuchElementException("Inconsistent record names: \"" + frLeft.name + "\" and \"" + frRight.name + "\"");
        }

        p.left = filterFastq(frLeft, qualPattern);
        
//        int numBasesTrimmed = frLeft.seq.length();
//        p.originalLeftLength = numBasesTrimmed;
//        for (String s : p.left) {
//            numBasesTrimmed -= s.length();
//        }
//        p.numLeftBasesTrimmed = numBasesTrimmed;
        
        p.right = filterFastq(frRight, qualPattern);
        
//        numBasesTrimmed = frRight.seq.length();
//        p.originalRightLength = numBasesTrimmed;
//        for (String s : p.right) {
//            numBasesTrimmed -= s.length();
//        }
//        p.numRightBasesTrimmed = numBasesTrimmed;
        
        return p;
    }

    private FastqReadPair nextFR() {
        FastqReadPair p = nextFF();
        
        ArrayList<String> right = p.right;
        int numRightSegments = right.size();
        
        if (numRightSegments > 1) {
            Collections.reverse(right);
        }
        
        for (int i=0; i<numRightSegments; ++i) {
            right.set(i, reverseComplement(right.get(i)));
        }
        
        //p.right = right;
        
        return p;
    }

    private FastqReadPair nextRF() {
        FastqReadPair p = nextFF();

        ArrayList<String> left = p.left;
        int numLeftSegments = left.size();
        
        if (numLeftSegments > 1) {
            Collections.reverse(left);
        }
        
        for (int i=0; i<numLeftSegments; ++i) {
            left.set(i, reverseComplement(left.get(i)));
        }
        
        //p.left = left;
        
        return p;
    }

    private FastqReadPair nextRR() {
        FastqReadPair p = nextFF();
        
        ArrayList<String> left = p.left;
        int numLeftSegments = left.size();
        
        if (numLeftSegments > 1) {
            Collections.reverse(left);
        }
        
        for (int i=0; i<numLeftSegments; ++i) {
            left.set(i, reverseComplement(left.get(i)));
        }
        
        //p.left = left;
        
        ArrayList<String> right = p.right;
        int numRightSegments = right.size();
        
        if (numRightSegments > 1) {
            Collections.reverse(right);
        }
        
        for (int i=0; i<numRightSegments; ++i) {
            right.set(i, reverseComplement(right.get(i)));
        }
        
        //p.right = right;
        
        return p;
    } 
}
