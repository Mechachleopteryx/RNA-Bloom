/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rnabloom.graph;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import rnabloom.bloom.BloomFilter;
import rnabloom.bloom.CountingBloomFilter;
import rnabloom.bloom.PairedKeysBloomFilter;
import rnabloom.bloom.hash.HashFunction;
import rnabloom.bloom.hash.SmallestStrandHashFunction;
import static rnabloom.util.SeqUtils.kmerize;

/**
 *
 * @author kmnip
 */
public class BloomFilterDeBruijnGraph {
    
    private final static char[] NUCLEOTIDES = new char[] {'A','C','G','T'};
    private BloomFilter dbgbf = null;
    private CountingBloomFilter cbf = null;
    private PairedKeysBloomFilter pkbf = null;
    private final int dbgbfCbfMaxNumHash;
    private final HashFunction hashFunction;
    private final int k;
    private final int overlap;
    private int pairedKmersDistance;
    private final long pkbfNumBits;
    private final int pkbfNumHash;
    
    public BloomFilterDeBruijnGraph(long dbgbfNumBits,
                                    long cbfNumBytes,
                                    long pkbfNumBits,
                                    int dbgbfNumHash,
                                    int cbfNumHash,
                                    int pkbfNumHash,
                                    int seed,
                                    int k,
                                    boolean stranded) {
        this.k = k;
        this.overlap = k-1;
        if (stranded) {
            this.hashFunction = new HashFunction(seed, k);
        }
        else {
            this.hashFunction = new SmallestStrandHashFunction(seed, k);
        }
        this.dbgbfCbfMaxNumHash = Math.max(dbgbfNumHash, cbfNumHash);
        this.dbgbf = new BloomFilter(dbgbfNumBits, dbgbfNumHash, this.hashFunction);
        this.cbf = new CountingBloomFilter(cbfNumBytes, cbfNumHash, this.hashFunction);
        this.pkbfNumBits = pkbfNumBits;
        this.pkbfNumHash = pkbfNumHash;
    }
    
    public void save(File desc) {
        
    }
    
    public void restore(File desc) {
        
    }
    
    public void initializePairKmersBloomFilter() {
        this.pkbf = new PairedKeysBloomFilter(pkbfNumBits, pkbfNumHash, this.hashFunction);
    }
    
    public void setPairedKmerDistance(int d) {
        this.pairedKmersDistance = d;
    }
    
    public int getPairedKmerDistance() {
        return this.pairedKmersDistance;
    }
    
    public int getK() {
        return k;
    }
    
    public void add(String kmer) {
        final long[] hashVals = hashFunction.getHashValues(kmer, dbgbfCbfMaxNumHash);
        dbgbf.add(hashVals);
        cbf.increment(hashVals);
    }
    
    public void addKmersFromSeq(String seq) {
        final int numKmers = seq.length()-k+1;
        
        for (int i=0; i<numKmers; ++i) {
            long[] hashVals = hashFunction.getHashValues(seq.substring(i, i+k), dbgbfCbfMaxNumHash);
            dbgbf.add(hashVals);
            cbf.increment(hashVals);
        }
    }
    
    public void addFragmentKmersFromSeq(String seq) {
        int numKmers = seq.length()-k+1;
        
        for (int i=0; i<numKmers; ++i) {
            pkbf.add(seq.substring(i, i+k));
        }
    }
    
    public void addPairedKmersFromSeq(String seq) {
        int numKmers = seq.length()-k+1;
        
        // add kmers
        for (int i=0; i<numKmers; ++i) {
            pkbf.add(seq.substring(i, i+k));
        }
        
        // add paired kmers
        int upperBound = numKmers-pairedKmersDistance;
        for (int i=0; i<upperBound; ++i) {
            pkbf.addPair(seq.substring(i, i+k), seq.substring(i+pairedKmersDistance, i+k+pairedKmersDistance));
        }
    }
    
    public boolean lookupFragmentKmer(String kmer) {
        return pkbf.lookup(kmer);
    }

    public boolean lookupPairedKmers(String kmer1, String kmer2) {
        return pkbf.lookupSingleAndPair(kmer1, kmer2);
    }
    
    public boolean contains(String kmer) {
        return dbgbf.lookup(kmer);
    }

    public void increment(String kmer) {
        cbf.increment(kmer);
    }
    
    public float getCount(String kmer) {
        final long[] hashVals = hashFunction.getHashValues(kmer, dbgbfCbfMaxNumHash);
        if (dbgbf.lookup(hashVals)) {
            return cbf.getCount(hashVals);
        }
        else {
            return 0;
        }
    }

    public float getFPR() {
        return dbgbf.getFPR() * cbf.getFPR();
    }
    
    public Kmer getKmer(String kmer) {
        return new Kmer(kmer, this.getCount(kmer));
    }
    
    public static class Kmer {
        public String seq;
        public float count;
        
        public Kmer(String seq, float count) {
            this.seq = seq;
            this.count = count;
        }
        
        public boolean equals(Kmer other) {
            return this.seq.equals(other.seq);
        }
    }
    
    private String getPrefix(String kmer) {
        return kmer.substring(0, overlap);
    }
    
    private String getSuffix(String kmer) {
        return kmer.substring(1, k);
    }
    
    public LinkedList<Kmer> getPredecessors(Kmer kmer) {
        LinkedList<Kmer> result = new LinkedList<>();
        String prefix = getPrefix(kmer.seq);
        String v;
        long[] hashVals;
        float count;
        for (char c : NUCLEOTIDES) {
            v = c + prefix;
            hashVals = this.hashFunction.getHashValues(v, dbgbfCbfMaxNumHash);
            if (dbgbf.lookup(hashVals)) {
                count = cbf.getCount(hashVals);
                if (count > 0) {
                    result.add(new Kmer(v, count));
                }
            }
        }
        return result;
    }
    
    public LinkedList<String> getPredecessors(String kmer) {
        LinkedList<String> result = new LinkedList<>();
        String prefix = getPrefix(kmer);
        String v;
        long[] hashVals;
        float count;
        for (char c : NUCLEOTIDES) {
            v = c + prefix;
            hashVals = this.hashFunction.getHashValues(v, dbgbfCbfMaxNumHash);
            if (dbgbf.lookup(hashVals)) {
                count = cbf.getCount(hashVals);
                if (count > 0) {
                    result.add(v);
                }
            }
        }
        return result;
    }

    public LinkedList<Kmer> getSuccessors(Kmer kmer) {
        LinkedList<Kmer> result = new LinkedList<>();
        String suffix = getSuffix(kmer.seq);
        String v;
        long[] hashVals;
        float count;
        for (char c : NUCLEOTIDES) {
            v = suffix + c;
            hashVals = this.hashFunction.getHashValues(v, dbgbfCbfMaxNumHash);
            if (dbgbf.lookup(hashVals)) {
                count = cbf.getCount(hashVals);
                if (count > 0) {
                    result.add(new Kmer(v, count));
                }
            }
        }
        return result;
    }
    
    public LinkedList<String> getSuccessors(String kmer) {
        LinkedList<String> result = new LinkedList<>();
        String suffix = getSuffix(kmer);
        String v;
        long[] hashVals;
        float count;
        for (char c : NUCLEOTIDES) {
            v = suffix + c;
            hashVals = this.hashFunction.getHashValues(v, dbgbfCbfMaxNumHash);
            if (dbgbf.lookup(hashVals)) {
                count = cbf.getCount(hashVals);
                if (count > 0) {
                    result.add(v);
                }
            }
        }
        return result;
    }
    
    public LinkedList<String> getLeftVariants(String kmer) {
        LinkedList<String> result = new LinkedList<>();
        String suffix = getSuffix(kmer);
        String v;
        for (char c : NUCLEOTIDES) {
            v = c + suffix;
            if (contains(v)) {
                result.add(v);
            }
        }
        return result;
    }

    public LinkedList<String> getRightVariants(String kmer) {
        LinkedList<String> result = new LinkedList<>();
        
        String prefix = getPrefix(kmer);
        String v;
        for (char c : NUCLEOTIDES) {
            v = prefix + c;
            if (contains(v)) {
                result.add(v);
            }
        }
        return result;
    }
    
    public float[] getCounts(String[] kmers){
        int numKmers = kmers.length;
        float[] counts = new float[numKmers];
        for (int i=0; i<numKmers; ++i) {
            counts[i] = getCount(kmers[i]);
        }
        return counts;
    }
    
    public float getMedianKmerCoverage(String seq){
        return getMedianKmerCoverage(kmerize(seq, k));
    }
    
    public float getMedianKmerCoverage(String[] kmers) {
        int numKmers = kmers.length;
        int halfNumKmers = numKmers/2;
        
        ArrayList<Float> counts = new ArrayList<>(numKmers);
        for (String kmer : kmers) {
            counts.add(cbf.getCount(kmer));
        }
        
        Collections.sort(counts);
        
        if (numKmers % 2 == 0) {
            return (counts.get(halfNumKmers) + counts.get(halfNumKmers -1))/2.0f;
        }
        
        return counts.get(halfNumKmers);
    }
        
    public boolean isValidSeq(String seq) {
        return containsAll(kmerize(seq, k));
    }
    
    public boolean isValidSeq(Collection<Kmer> kmers) {
        for (Kmer kmer : kmers) {
            if (kmer.count == 0) {
                return false;
            }
        }
        return true;
    }
    
    public boolean containsAll(String[] kmers) {
        for (String kmer : kmers) {
            if (!contains(kmer)) {
                return false;
            }
        }
        return true;
    }
    
    public ArrayList<Kmer> getKmers(String seq) {
        final int numKmers = seq.length()-k+1;
        ArrayList<Kmer> result = new ArrayList<>(numKmers);
        
        for (int i=0; i<numKmers; ++i) {
            result.add(getKmer(seq.substring(i, i+k)));
        }
        
        return result;
    }
}
