/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rnabloom.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import rnabloom.graph.BloomFilterDeBruijnGraph;
import rnabloom.graph.BloomFilterDeBruijnGraph.Kmer;

/**
 *
 * @author gengar
 */
public final class GraphUtils {
    
    public static Kmer greedyExtendRightOnce(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        ArrayList<Kmer> candidates = graph.getSuccessors(source);
        
        if (candidates.isEmpty()) {
            return null;
        }
        else {
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            else {
                ArrayList<Kmer> alts = graph.getSuccessors(source);
                Kmer cursor = alts.remove(alts.size()-1);
                
                ArrayList<Kmer> path = new ArrayList<>(lookahead); 
                path.add(cursor);
                
                ArrayList<ArrayList<Kmer>> frontier = new ArrayList<>(lookahead);
                frontier.add(alts);
                
                float bestCov = 0;
                int bestLen = 1;
                ArrayList<Kmer> bestPath = path;
                
                while (!frontier.isEmpty()) {
                    if (path.size() < lookahead) {
                        alts = graph.getSuccessors(cursor);
                        if (!alts.isEmpty()) {
                            cursor = alts.remove(alts.size()-1);
                            path.add(cursor);
                            frontier.add(alts);
                            continue;
                        }
                    }
                    
                    float pathCov = graph.getMedianKmerCoverage(path);
                    int pathLen = path.size();
                    if (bestLen < pathLen || bestCov < pathCov) {
                        bestPath = new ArrayList<>(path);
                        bestCov = pathCov;
                        bestLen = pathLen;
                    }

                    int i = path.size()-1;
                    while (i >= 0) {
                        alts = frontier.get(i);
                        path.remove(i);
                        if (alts.isEmpty()) {
                            frontier.remove(i);
                            --i;
                        }
                        else {
                            cursor = alts.remove(alts.size()-1);
                            path.add(cursor);
                            break;
                        }
                    }
                }
                
                return bestPath.get(0);
            }
        }
    }
    
    public static Kmer greedyExtendLeftOnce(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        ArrayList<Kmer> candidates = graph.getPredecessors(source);
        
        if (candidates.isEmpty()) {
            return null;
        }
        else {
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            else {
                ArrayList<Kmer> alts = graph.getPredecessors(source);
                Kmer cursor = alts.remove(alts.size()-1);
                
                ArrayList<Kmer> path = new ArrayList<>(lookahead); 
                path.add(cursor);
                
                ArrayList<ArrayList<Kmer>> frontier = new ArrayList<>(lookahead);
                frontier.add(alts);
                
                float bestCov = 0;
                int bestLen = 1;
                ArrayList<Kmer> bestPath = path;
                
                while (!frontier.isEmpty()) {
                    if (path.size() < lookahead) {
                        alts = graph.getPredecessors(cursor);
                        if (!alts.isEmpty()) {
                            cursor = alts.remove(alts.size()-1);
                            path.add(cursor);
                            frontier.add(alts);
                            continue;
                        }
                    }
                    
                    float pathCov = graph.getMedianKmerCoverage(path);
                    int pathLen = path.size();
                    if (bestLen < pathLen || bestCov < pathCov) {
                        bestPath = new ArrayList<>(path);
                        bestCov = pathCov;
                        bestLen = pathLen;
                    }

                    int i = path.size()-1;
                    while (i >= 0) {
                        alts = frontier.get(i);
                        path.remove(i);
                        if (alts.isEmpty()) {
                            frontier.remove(i);
                            --i;
                        }
                        else {
                            cursor = alts.remove(alts.size()-1);
                            path.add(cursor);
                            break;
                        }
                    }
                }
                
                return bestPath.get(0);
            }
        }
    }
    
    /**
     * 
     * @param graph
     * @param left
     * @param right
     * @param bound
     * @param lookahead
     * @return 
     */
    public static ArrayList<Kmer> getMaxCoveragePath(BloomFilterDeBruijnGraph graph, Kmer left, Kmer right, int bound, int lookahead) {
        
        HashSet<String> leftPathKmers = new HashSet<>(bound);
        
        /* extend right */
        ArrayList<Kmer> leftPath = new ArrayList<>(bound);
        Kmer best = left;
        ArrayList<Kmer> neighbors;
        for (int depth=0; depth < bound; ++depth) {
            neighbors = graph.getSuccessors(best);
            
            if (neighbors.isEmpty()) {
                break;
            }
            else {
                if (neighbors.size() == 1) {
                    best = neighbors.get(0);
                }
                else {
                    best = greedyExtendRightOnce(graph, best, lookahead);
                }
                
                if (best.equals(right)) {
                    return leftPath;
                }
                else {
                    leftPath.add(best);
                }
            }
        }
        
        for (Kmer kmer : leftPath) {
            leftPathKmers.add(kmer.seq);
        }
        
        /* not connected, search from right */
        ArrayList<Kmer> rightPath = new ArrayList<>(bound);
        best = right;
        for (int depth=0; depth < bound; ++depth) {
            neighbors = graph.getPredecessors(best);
            
            if (neighbors.isEmpty()) {
                break;
            }
            else {
                if (neighbors.size() == 1) {
                    best = neighbors.get(0);
                }
                else {
                    best = greedyExtendLeftOnce(graph, best, lookahead);
                }
                
                if (best.equals(left)) {
                    Collections.reverse(rightPath);
                    return rightPath;
                }
                else if (leftPathKmers.contains(best.seq)) {
                    /*right path intersects the left path */
                    String convergingKmer = best.seq;
                    ArrayList<Kmer> path = new ArrayList<>(bound);
                    for (Kmer kmer : leftPath) {
                        if (convergingKmer.equals(kmer.seq)) {
                            break;
                        }
                        else {
                            path.add(kmer);
                        }
                    }
                    Collections.reverse(rightPath);
                    path.addAll(rightPath);
                    return path;
                }
                else {
                    rightPath.add(best);
                }
            }
        }
        
        return null;
    }
    
    private static float getMedian(float[] a) {
        Arrays.sort(a);
        int halfLen = a.length/2;
        if (halfLen % 2 == 0) {
            return (a[halfLen-1] + a[halfLen])/2.0f;
        }
        
        return a[halfLen];
    }
    
    private static float getMedian(ArrayList<Float> a) {
        Collections.sort(a);
        int halfLen = a.size()/2;
        if (halfLen % 2 == 0) {
            return (a.get(halfLen-1) + a.get(halfLen))/2.0f;
        }
        
        return a.get(halfLen);
    }
    
    private static float rightGuidedMedianCoverageHelper(BloomFilterDeBruijnGraph graph, Kmer left, String guide) {
        int guideLen = guide.length();
        float[] covs = new float[guideLen+1];
        covs[guideLen] = left.count;
        
        String postfix = left.seq.substring(1);
        String kmer;
        float count;
        for (int i=0; i<guideLen; ++i) {
            kmer = postfix + guide.charAt(i);
            count = graph.getCount(kmer);
            if (count > 0) {
                covs[i] = count;
                postfix = kmer.substring(1);
            }
            else {
                // not a valid sequence
                return 0;
            }
        }
        
        return getMedian(covs);
    }

    private static float leftGuidedMedianCoverageHelper(BloomFilterDeBruijnGraph graph, Kmer right, String guide) {
        int guideLen = guide.length();
        float[] covs = new float[guideLen+1];
        covs[0] = right.count;
        
        int kMinus1 = graph.getK()-1;
        String prefix = right.seq.substring(0,kMinus1);
        String kmer;
        float count;
        for (int i=guideLen-1; i>0; --i) {
            kmer = guide.charAt(i) + prefix;
            count = graph.getCount(kmer);
            if (count > 0) {
                covs[i] = count;
                prefix = kmer.substring(0,kMinus1);
            }
            else {
                // not a valid sequence
                return 0;
            }
        }
        
        return getMedian(covs);
    }
    
    public static ArrayList<Kmer> correctMismatches(String seq, BloomFilterDeBruijnGraph graph, int lookahead, int mismatchesAllowed) {
        int seqLen = seq.length();
        int k = graph.getK();
        
        int numKmers = seqLen - k + 1;
        
        if (numKmers > 1) {
            char[] correctedSeq = new char[seq.length()];
            
            int mismatchesCorrected = 0;

            String currKmerSeq;
            Kmer prevKmer = graph.getKmer(seq.substring(0,k));
            String guide;
            Kmer bestKmer;
            float bestCov;
            float count;

            for (int i=k; i<seqLen-1; ++i) {
                currKmerSeq = graph.getSuffix(prevKmer.seq) + seq.charAt(i);
                guide = seq.substring(i+1, Math.min(i+1+lookahead, seqLen));

                bestKmer = null;
                bestCov = 0;

                for (Kmer s : graph.getSuccessors(prevKmer)) {
                    count = rightGuidedMedianCoverageHelper(graph, s, guide);
                    if (count > bestCov) {
                        bestKmer = s;
                        bestCov = count;
                    }
                }

                if (bestKmer == null) {
                    // undo all corrections
                    return graph.getKmers(seq);
                }
                else {
                    if (!currKmerSeq.equals(bestKmer.seq) && ++mismatchesCorrected > mismatchesAllowed) {
                        // too many mismatches, undo all corrections
                        return graph.getKmers(seq);
                    }

                    correctedSeq[i] = graph.getLastBase(bestKmer.seq);
                    prevKmer = bestKmer;
                }
            }
            
            bestKmer = greedyExtendRightOnce(graph, prevKmer, lookahead);
            correctedSeq[seqLen-1] = graph.getLastBase(bestKmer.seq);
            
            /** correct mismatches in first kmer of the sequence*/
            
            /** Get the k-th kmer or the last kmer, whichever is more to the left*/
            int i = Math.min(2*k, seqLen);
            prevKmer = graph.getKmer(seq.substring(i-k,i));
            
            for (i=i-k-1; i>0; --i) {
                currKmerSeq = seq.charAt(i) + graph.getPrefix(prevKmer.seq);
                guide = seq.substring(Math.max(0, i-lookahead), i);
                
                bestKmer = null;
                bestCov = 0;

                for (Kmer s : graph.getPredecessors(prevKmer)) {
                    count = leftGuidedMedianCoverageHelper(graph, s, guide);
                    if (count > bestCov) {
                        bestKmer = s;
                        bestCov = count;
                    }
                }

                if (bestKmer == null) {
                    // undo all corrections
                    return graph.getKmers(seq);
                }
                else {
                    if (!currKmerSeq.equals(bestKmer.seq) && ++mismatchesCorrected > mismatchesAllowed) {
                        // too many mismatches, undo all corrections
                        return graph.getKmers(seq);
                    }

                    correctedSeq[i] = graph.getFirstBase(bestKmer.seq);
                    prevKmer = bestKmer;
                }
            }
            
            bestKmer = greedyExtendLeftOnce(graph, prevKmer, lookahead);
            correctedSeq[0] = graph.getFirstBase(bestKmer.seq);
            
            return graph.getKmers(new String(correctedSeq));
        }
        else {
            return graph.getKmers(seq);
        }
    }

    public static String assemble(ArrayList<Kmer> kmers) {
        
        String first = kmers.get(0).seq;
        int k = first.length();
        int lastIndex = k - 1;
        
        StringBuilder sb = new StringBuilder(k + kmers.size() - 1);
        sb.append(first.substring(0, lastIndex));
        
        for (Kmer kmer : kmers) {
            sb.append(kmer.seq.charAt(lastIndex));
        }
        
        return sb.toString();
    }    
    
    public static ArrayList<Kmer> greedyExtend(Kmer seed, BloomFilterDeBruijnGraph graph, int lookahead) {
        ArrayList<Kmer> rightPath = new ArrayList<>(100);
        
        /* extend on right side */
        Kmer best = seed;
        while (best != null) {
            best = greedyExtendRightOnce(graph, best, lookahead);
            rightPath.add(best);
        }
        
        ArrayList<Kmer> leftPath = new ArrayList<>(100);
        
        /* extend on left side */
        best = seed;
        while (best != null) {
            best = greedyExtendLeftOnce(graph, best, lookahead);
            leftPath.add(best);
        }
        
        Collections.reverse(leftPath);
        leftPath.add(seed);
        leftPath.addAll(rightPath);
        
        return leftPath;
    }
    
    public static ArrayList<Kmer> findBackbonePath(Kmer seed, BloomFilterDeBruijnGraph graph, int lookahead, int windowSize, int maxIteration) {
        ArrayList<Kmer> path = null;
        
        Kmer best = seed;
        
        for (int i=0; i<maxIteration; ++i) {
            path = greedyExtend(best, graph, lookahead);
            float maxCov = 0;
            float cov;
            
            int start = 0;
            int end = windowSize;
            
            int maxIndex = path.size() - windowSize;
            ArrayList<Float> counts = new ArrayList<>(windowSize);
            
            for (int j=0; j<maxIndex; ++j) {
                counts.add(path.get(j).count);
                
                if (counts.size() >= windowSize) {
                    cov = getMedian(counts);
                    if (cov > maxCov) {
                        maxCov = cov;
                        start = j-windowSize;
                        end = j;
                    }
                    
                    counts.remove(0);
                    counts.add(path.get(j).count);
                }
            }
            
            best = path.get((end-start)/2);
        }
        
        return path;
    }
    
    public static String assembleFragment(String left, String right, BloomFilterDeBruijnGraph graph, int defaultBound, int maxTipLength, int sampleSize) {
        String fragment = null;
        
        /**@TODO*/
        
        
        return fragment;
    }
}
