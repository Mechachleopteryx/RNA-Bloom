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
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import rnabloom.RNABloom.KmerSet;
import rnabloom.graph.BloomFilterDeBruijnGraph;
import rnabloom.graph.BloomFilterDeBruijnGraph.Kmer;
import static rnabloom.util.SeqUtils.getFirstKmer;
import static rnabloom.util.SeqUtils.getLastKmer;
import static rnabloom.util.SeqUtils.kmerizeToCollection;
import static rnabloom.util.SeqUtils.overlapMaximally;

/**
 *
 * @author gengar
 */
public final class GraphUtils {
    
    public static float getMedianKmerCoverage(final ArrayList<Kmer> kmers) {
        int numKmers = kmers.size();
        int halfNumKmers = numKmers/2;
        
        ArrayList<Float> counts = new ArrayList<>(numKmers);
        for (Kmer kmer : kmers) {
            counts.add(kmer.count);
        }
        
        Collections.sort(counts);
        
        if (numKmers % 2 == 0) {
            return (counts.get(halfNumKmers) + counts.get(halfNumKmers -1))/2.0f;
        }
        
        return counts.get(halfNumKmers);
    }
    
    public static float getMaxMedianCoverageRight(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        Iterator<Kmer> itr = graph.getSuccessors(source).iterator();
        
        if (!itr.hasNext()) {
            return source.count;
        }
        else {
            Kmer cursor = itr.next();
            ArrayList<Kmer> path = new ArrayList<>(lookahead); 
            path.add(source);
            path.add(cursor);

            ArrayList<Iterator<Kmer>> frontier = new ArrayList<>(lookahead);
            frontier.add(itr);

            float bestCov = 0;

            while (!frontier.isEmpty()) {
                if (path.size() < lookahead) {
                    itr = graph.getSuccessors(cursor).iterator();
                    if (itr.hasNext()) {
                        cursor = itr.next();
                        path.add(cursor);
                        frontier.add(itr);
                        continue;
                    }
                }

                float pathCov = getMedianKmerCoverage(path);
                if (bestCov < pathCov) {
                    bestCov = pathCov;
                }

                int i = path.size()-2;
                while (i >= 0) {
                    itr = frontier.get(i);
                    path.remove(i+1);
                    if (!itr.hasNext()) {
                        frontier.remove(i);
                        --i;
                    }
                    else {
                        cursor = itr.next();
                        path.add(cursor);
                        break;
                    }
                }
            }
            
            return bestCov;
        }
    }

    public static float getMaxMedianCoverageLeft(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        Iterator<Kmer> itr = graph.getPredecessors(source).iterator();
        
        if (!itr.hasNext()) {
            return source.count;
        }
        else {
            Kmer cursor = itr.next();
            ArrayList<Kmer> path = new ArrayList<>(lookahead);
            path.add(source);
            path.add(cursor);

            ArrayList<Iterator<Kmer>> frontier = new ArrayList<>(lookahead);
            frontier.add(itr);

            float bestCov = 0;

            while (!frontier.isEmpty()) {
                if (path.size() < lookahead) {
                    itr = graph.getPredecessors(cursor).iterator();
                    if (itr.hasNext()) {
                        cursor = itr.next();
                        path.add(cursor);
                        frontier.add(itr);
                        continue;
                    }
                }

                float pathCov = getMedianKmerCoverage(path);
                if (bestCov < pathCov) {
                    bestCov = pathCov;
                }

                int i = path.size()-2;
                while (i >= 0) {
                    itr = frontier.get(i);
                    path.remove(i+1);
                    if (!itr.hasNext()) {
                        frontier.remove(i);
                        --i;
                    }
                    else {
                        cursor = itr.next();
                        path.add(cursor);
                        break;
                    }
                }
            }

            return bestCov;
        }
    }
    
    public static Kmer greedyExtendRightOnce(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        LinkedList<Kmer> candidates = graph.getSuccessors(source);
        
        if (candidates.isEmpty()) {
            return null;
        }
        else {
            if (candidates.size() == 1) {
                return candidates.peek();
            }
            else {
                float bestCov = -1;
                Kmer bestKmer = null;
                for (Kmer kmer : candidates) {
                    float c = getMaxMedianCoverageRight(graph, kmer, lookahead);
                    if (c > bestCov) {
                        bestKmer = kmer;
                        bestCov = c;
                    }
                }
                return bestKmer;
            }
        }
    }
    
    public static Kmer greedyExtendLeftOnce(BloomFilterDeBruijnGraph graph, Kmer source, int lookahead) {
        LinkedList<Kmer> candidates = graph.getPredecessors(source);
        
        if (candidates.isEmpty()) {
            return null;
        }
        else {
            if (candidates.size() == 1) {
                return candidates.peek();
            }
            else {
                float bestCov = -1;
                Kmer bestKmer = null;
                for (Kmer kmer : candidates) {
                    float c = getMaxMedianCoverageLeft(graph, kmer, lookahead);
                    if (c > bestCov) {
                        bestKmer = kmer;
                        bestCov = c;
                    }
                }
                return bestKmer;
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
        LinkedList<Kmer> neighbors;
        for (int depth=0; depth < bound; ++depth) {
            neighbors = graph.getSuccessors(best);
            
            if (neighbors.isEmpty()) {
                break;
            }
            else {
                if (neighbors.size() == 1) {
                    best = neighbors.peek();
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
                    best = neighbors.peek();
                }
                else {
                    best = greedyExtendLeftOnce(graph, best, lookahead);
                }
                
                if (best.equals(left)) {
                    Collections.reverse(rightPath);
                    return rightPath;
                }
                else if (leftPathKmers.contains(best.seq)) {
                    /* right path intersects the left path */
                    String convergingKmer = best.seq;
                    ArrayList<Kmer> path = new ArrayList<>(bound);
                    for (Kmer kmer : leftPath) {
                        path.add(kmer);
                        if (convergingKmer.equals(kmer.seq)) {
                            break;
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
    
    public static float getMedian(float[] arr) {
        float[] a = Arrays.copyOf(arr, arr.length);
        Arrays.sort(a);
        int halfLen = a.length/2;
        if (halfLen % 2 == 0) {
            return (a[halfLen-1] + a[halfLen])/2.0f;
        }
        
        return a[halfLen];
    }
        
    public static float getMedian(List<Float> arr) {
        ArrayList<Float> a = new ArrayList<>(arr);
        Collections.sort(a);
        int halfLen = a.size()/2;
        if (halfLen % 2 == 0) {
            return (a.get(halfLen-1) + a.get(halfLen))/2.0f;
        }
        
        return a.get(halfLen);
    }
        
    private static class Stats {
        public float min;
        public float q1;
        public float median;
        public float q3;
        public float max;
    }
    
    private static Stats getStats(final List<Float> arr) {
        ArrayList<Float> a = new ArrayList<>(arr);
        Collections.sort(a);
        
        Stats stats = new Stats();
        
        int arrLen = a.size();
        int halfLen = arrLen/2;
        int q1Index = arrLen/4;
        int q3Index = halfLen+q1Index;
        
        stats.min = a.get(0);
        stats.max = a.get(arrLen-1);
        
        if (arrLen % 2 == 0) {
            stats.median = (a.get(halfLen-1) + a.get(halfLen))/2.0f;
        }
        else {
            stats.median = a.get(halfLen);
        }
        
        if (arrLen % 4 == 0) {
            stats.q1 = (a.get(q1Index-1) + a.get(q1Index))/2.0f;
            stats.q3 = (a.get(q3Index-1) + a.get(q3Index))/2.0f;
        }
        else {
            stats.q1 = a.get(q1Index);
            stats.q3 = a.get(q3Index);
        }
        
        return stats;
    }
    
    private static float rightGuidedMedianCoverage(BloomFilterDeBruijnGraph graph, String source, String guide) {
        int guideLen = guide.length();
        if (guideLen == 0) {
            return graph.getCount(source);
        }
        
        float[] covs = new float[guideLen+1];
        covs[guideLen] = graph.getCount(source);
        
        String postfix = source.substring(1);
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
    
    private static float leftGuidedMedianCoverage(BloomFilterDeBruijnGraph graph, String source, String guide) {
        int guideLen = guide.length();
        if (guideLen == 0) {
            return graph.getCount(source);
        }
        
        float[] covs = new float[guideLen+1];
        covs[0] = graph.getCount(source);
        
        int kMinus1 = graph.getK()-1;
        String prefix = source.substring(0,kMinus1);
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
    
    public static String correctMismatches(String seq, BloomFilterDeBruijnGraph graph, int lookahead, int mismatchesAllowed) {
        int seqLen = seq.length();
        int k = graph.getK();
        
        if (seqLen < k) {
            // no correction
            return seq;
        }
        
        int numKmers = seqLen-k+1;
        StringBuilder sb = new StringBuilder(seq);
        
        float bestCov, cov;
        String kmer, guide;
        LinkedList<String> variants;
        
        // correct from start
        for (int i=0; i<numKmers; ++i) {
            int end = i+k;
            kmer = sb.substring(i, end);
            variants = graph.getRightVariants(kmer);
            if (!variants.isEmpty()) {
                guide = sb.substring(end, Math.min(end+lookahead, seqLen));
                bestCov = rightGuidedMedianCoverage(graph, kmer, guide);
                
                boolean corrected = false;
                for (String v : variants) {
                    cov = rightGuidedMedianCoverage(graph, v, guide);
                    if (cov > bestCov) {
                        bestCov = cov;
                        sb.setCharAt(end-1, v.charAt(k-1));
                        corrected = true;
                    }
                }
                
                if (corrected) {
                    if (--mismatchesAllowed < 0) {
                        // too many mismatches
                        return seq;
                    }
                }
            }
        }
        
        // correct from end
        for (int i=seqLen-k; i>=0; --i) {
            kmer = sb.substring(i, i+k);
            variants = graph.getLeftVariants(kmer);
            if (!variants.isEmpty()) {
                guide = sb.substring(Math.max(0, i-lookahead), i);
                bestCov = leftGuidedMedianCoverage(graph, kmer, guide);
                
                boolean corrected = false;
                for (String v : variants) {
                    cov = leftGuidedMedianCoverage(graph, v, guide);
                    if (cov > bestCov) {
                        bestCov = cov;
                        sb.setCharAt(i, v.charAt(0));
                        corrected = true;
                    }
                }
                
                if (corrected) {
                    if (--mismatchesAllowed < 0) {
                        // too many mismatches
                        return seq;
                    }
                }
            }
        }
        
        String seq2 = sb.toString();
        
        if (!graph.isValidSeq(seq2)) {
            return seq;
        }
        
        return seq2;
    }
    
    public static float[] coverageGradients(String seq, BloomFilterDeBruijnGraph graph, int lookahead) {
        float[] counts = graph.getCounts(seq);
        int numCounts = counts.length;
        
        LinkedList<Float> window = new LinkedList<>();
        for (int i=0; i<lookahead; ++i) {
            window.addLast(counts[i]);
        }        

        int numMedCounts = numCounts-lookahead+1;
        float[] medCounts = new float[numMedCounts];
        medCounts[0] = getMedian(window);
        int m = 0;
        for (int i=lookahead; i<numCounts; ++i) {
            window.removeFirst();
            window.addLast(counts[i]);
            medCounts[++m] = getMedian(window);
        }
        
        int numGradients = numCounts-(2*lookahead)+1;
        float[] gradients = new float[numGradients];
        for (int i=0; i<numGradients; ++i) {
            float r = medCounts[i]/medCounts[i+lookahead];
            if (r > 1) {
                gradients[i] = 1/r;
            }
            else {
                gradients[i] = r;
            }
        }
        
        return gradients;
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

    public static String assembleString(ArrayList<String> kmers) {
        
        String first = kmers.get(0);
        int k = first.length();
        int lastIndex = k - 1;
        
        StringBuilder sb = new StringBuilder(k + kmers.size() - 1);
        sb.append(first.substring(0, lastIndex));
        
        for (String kmer : kmers) {
            sb.append(kmer.charAt(lastIndex));
        }
        
        return sb.toString();
    }
    
    public static String assembleFirstBase(ArrayList<Kmer> kmers) {
        StringBuilder sb = new StringBuilder(kmers.size());
        for (Kmer kmer : kmers) {
            sb.append(kmer.seq.charAt(0));
        }
        
        return sb.toString();
    }

    public static String assembleLastBase(ArrayList<Kmer> kmers) {
        int lastIndex = kmers.get(0).seq.length() - 1;
        
        StringBuilder sb = new StringBuilder(kmers.size());
        for (Kmer kmer : kmers) {
            sb.append(kmer.seq.charAt(lastIndex));
        }
        
        return sb.toString();
    }
    
    public static ArrayList<Kmer> greedyExtend(Kmer seed, BloomFilterDeBruijnGraph graph, int lookahead) {
        /**@TODO store smallest strand kmers for non-strand specific sequences */
        
        HashSet<String> pathKmerStr = new HashSet<>(1000);
        pathKmerStr.add(seed.seq);
        
        ArrayList<Kmer> rightPath = new ArrayList<>(1000);
        
        /* extend on right side */
        Kmer best = seed;
        while (true) {
            best = greedyExtendRightOnce(graph, best, lookahead);
            if (best != null) {
                String seq = best.seq;
                if (pathKmerStr.contains(seq)) {
                    break;
                }
                pathKmerStr.add(seq);
                rightPath.add(best);
            }
            else {
                break;
            }
        }
        
        ArrayList<Kmer> leftPath = new ArrayList<>(100);
        
        /* extend on left side */
        best = seed;
        while (true) {
            best = greedyExtendLeftOnce(graph, best, lookahead);
            if (best != null) {
                String seq = best.seq;
                if (pathKmerStr.contains(seq)) {
                    break;
                }
                pathKmerStr.add(seq);
                leftPath.add(best);
            }
            else {
                break;
            }
        }
        
        Collections.reverse(leftPath);
        leftPath.add(seed);
        leftPath.addAll(rightPath);
        
        return leftPath;
    }
    
    public static Kmer findMaxCoverageWindowKmer(ArrayList<Kmer> path, BloomFilterDeBruijnGraph graph, int windowSize) {
        int pathLen = path.size();
        if (pathLen <= windowSize) {
            return path.get(pathLen/2);
        }
                
        LinkedList<Float> window = new LinkedList<>();
        for (int i=0; i<windowSize; ++i) {
            window.addFirst(path.get(i).count);
        }
        
        int start = 0;
        int end = windowSize;
        float maxCov = getMedian(window);
        
        float cov;
        for (int i=windowSize; i<pathLen; ++i) {
            window.removeLast();
            window.addFirst(path.get(i).count);
            cov = getMedian(window);
            if (cov > maxCov) {
                maxCov = cov;
                end = i+1;
                start = end-windowSize;
            }
        }
        
        return path.get((end+start)/2);
    }
    
    public static ArrayList<Kmer> findBackbonePath(Kmer seed, BloomFilterDeBruijnGraph graph, int lookahead, int windowSize, int maxIteration) {
        Kmer best = seed;
        ArrayList<Kmer> path = greedyExtend(best, graph, lookahead);
        boolean randomSeed = false;
        
        for (int i=1; i<maxIteration; ++i) {
            if (randomSeed) {
                best = path.get((int) (Math.random() * (path.size()-1)));
                randomSeed = false;
            }
            else {
                best = findMaxCoverageWindowKmer(path, graph, windowSize);
                randomSeed = true;
            }
            path = greedyExtend(best, graph, lookahead);
        }
        
        return path;
    }
    
    public static String connect(ArrayList<String> segments, BloomFilterDeBruijnGraph graph, int bound, int lookahead) {
        int numSeqs = segments.size();
        switch (numSeqs) {
            case 0:
                return "";
            case 1:
                return segments.get(0);
            default:
                String last = segments.get(0);
                String longest = last;
                
                for (int i=1; i<numSeqs; ++i) {
                    String current = segments.get(i);
                    
                    String connected = connect(last, current, graph, bound, lookahead);
                    int connectedLength = connected.length();
                    
                    if (connectedLength > 0) {
                        last = connected;
                        
                        if (connectedLength > longest.length()) {
                            longest = connected;
                        }
                    }
                    else {
                        last = current;
                        
                        if (current.length() > longest.length()) {
                            longest = current;
                        }
                    }
                }
                
                return longest;
        }
    }
    
    public static String connect(String left, String right, BloomFilterDeBruijnGraph graph, int bound, int lookahead) {
        int k = graph.getK();
        
        ArrayList<Kmer> pathKmers = getMaxCoveragePath(graph, graph.getKmer(getLastKmer(left, k)), graph.getKmer(getFirstKmer(right, k)), bound, lookahead);
        
        if (pathKmers == null || pathKmers.isEmpty()) {
            return "";
        }
        
        String leftWing, rightWing;
        int leftReadLength = left.length();
        if (leftReadLength == k) {
            // first base only
            leftWing = left.substring(0, 1);
        }
        else {
            leftWing = left.substring(0, leftReadLength-k+1);
        }
        
        rightWing = right.substring(k-1);
        
        return leftWing + assemble(pathKmers) + rightWing;
    }
    
    public static String overlapThenConnect(String left, String right, BloomFilterDeBruijnGraph graph, int bound, int lookahead, int minOverlap) {
        
        // overlap before finding path
        String overlapped = overlapMaximally(left, right, minOverlap);
        if (overlapped != null && graph.isValidSeq(overlapped)) {
            return overlapped;
        }
        
        return connect(left, right, graph, bound, lookahead);
    }
    
    public static String extendWithPairedKmers(String fragment, BloomFilterDeBruijnGraph graph, int lookahead) {
        final int distance = graph.getPairedKmerDistance();
        final int k = graph.getK();
        
        String transcript = fragment;
        
                
        
        return transcript;
    }
    
    public static String assembleTranscript(String fragment, BloomFilterDeBruijnGraph graph, int lookahead, float covGradient, KmerSet assembledKmers) {
        final int distance = graph.getPairedKmerDistance();
        final int k = graph.getK();
        
        final ArrayList<String> kmers = new ArrayList<>(2*(fragment.length()-k+1));
        kmerizeToCollection(fragment, k, kmers);
        final HashSet<String> transcriptKmers = new HashSet<>(kmers);
        final HashSet<String> usedPairs = new HashSet<>();
        
        float fragMinCov = Float.POSITIVE_INFINITY;
        for (String kmer : kmers) {
            float c = graph.getCount(kmer);
            if (c < fragMinCov) {
                fragMinCov = c;
            }
        }
        //System.out.println(fragMinCov);
        
        /** extend right*/
        String best = kmers.get(kmers.size()-1);
        LinkedList<String> neighbors = graph.getSuccessors(best);
        while (!neighbors.isEmpty()) {
            best = null;
            String partner = null;
            
            if (neighbors.size() == 1) {
                best = neighbors.peek();
            }
            else {
                // >1 neighbors
                
                LinkedList<String> fragmentNeighbors = new LinkedList<>();
                for (String n : neighbors) {
                    if (graph.lookupFragmentKmer(n)) {
                        fragmentNeighbors.add(n);
                    }
                }
                
                if (fragmentNeighbors.isEmpty()) {
                    // no neighbors with supporting fragments
                    break;
                }
                else if (fragmentNeighbors.size() == 1) {
                    best = fragmentNeighbors.peek();
                }
                else {
                    // >1 fragment neighbors
                    LinkedList<String> neighborsWithSimilarCoverage = new LinkedList<>();
                    
                    int numKmers = kmers.size();
                    LinkedList<Float> covs = new LinkedList<>();
                    for (int i=numKmers-lookahead; i<numKmers; ++i) {
                        covs.add(graph.getCount(kmers.get(i)));
                    }
                    float currentCov = getMedian(covs);
                    float min = currentCov * covGradient;
                    float cov = 0;
                    
                    for (String n : fragmentNeighbors) {
                        float c = getMaxMedianCoverageRight(graph, graph.getKmer(n), lookahead);
                        if (c >= min) {
                            if (c > cov) {
                                cov = c;
                                neighborsWithSimilarCoverage.addFirst(n);
                            }
                            else {
                                neighborsWithSimilarCoverage.addLast(n);
                            }
                        }
                    }
                    
                    if (neighborsWithSimilarCoverage.size() == 1) {
                        best = neighborsWithSimilarCoverage.peek();
                    }
                    else if (numKmers >= distance) {
                        LinkedList<String> pairedNeighbors = new LinkedList<>();
                        partner = kmers.get(numKmers-distance);
                        for (String n : fragmentNeighbors) {
                            if (graph.lookupPairedKmers(partner, n)) {
                                pairedNeighbors.add(n);
                            }
                        }
                        if (pairedNeighbors.size() == 1) {
                            best = pairedNeighbors.peek();
                        }
                    }
                    
                    if (best == null && neighborsWithSimilarCoverage.size() > 1) {
                        best = neighborsWithSimilarCoverage.getFirst();
                        if (assembledKmers.contains(best)) {
                            best = null;
                        }
                    }
                }
            }
            
            if (best == null) {
                break;
            }
            else {
                int numKmers = kmers.size();
                if (numKmers >= distance && partner == null) {
                    partner = kmers.get(numKmers-distance);
                }
                
                if (transcriptKmers.contains(best)) {
                    if (partner == null || !graph.lookupPairedKmers(partner, best)) {
                        break;
                    }
                    else {
                        String pairedKmersStr = partner + best;
                        if (usedPairs.contains(pairedKmersStr)) {
                            break;
                        }
                        else {
                            usedPairs.add(pairedKmersStr);
                        }
                    }
                }
                
                if (assembledKmers.contains(best) && (partner == null || assembledKmers.contains(partner))) {
                    break;
                }
            }
            
            kmers.add(best);
            transcriptKmers.add(best);
            neighbors = graph.getSuccessors(best);
        }
        
        Collections.reverse(kmers);
        
        /** extend left*/
        best = kmers.get(kmers.size()-1);
        neighbors = graph.getPredecessors(best);
        while (!neighbors.isEmpty()) {
            best = null;
            String partner = null;
            
            if (neighbors.size() == 1) {
                best = neighbors.peek();
            }
            else {
                // >1 neighbors
                
                LinkedList<String> fragmentNeighbors = new LinkedList<>();
                for (String n : neighbors) {
                    if (graph.lookupFragmentKmer(n)) {
                        fragmentNeighbors.add(n);
                    }
                }
                
                if (fragmentNeighbors.isEmpty()) {
                    break;
                }
                else if (fragmentNeighbors.size() == 1) {
                    best = fragmentNeighbors.peek();
                }
                else {
                    // >1 fragment neighbors
                    LinkedList<String> neighborsWithSimilarCoverage = new LinkedList<>();
                    
                    int numKmers = kmers.size();
                    LinkedList<Float> covs = new LinkedList<>();
                    for (int i=numKmers-lookahead; i<numKmers; ++i) {
                        covs.add(graph.getCount(kmers.get(i)));
                    }
                    float currentCov = getMedian(covs);
                    float min = currentCov * covGradient;
                    
                    float cov = 0;
                    
                    for (String n : fragmentNeighbors) {
                        float c = getMaxMedianCoverageLeft(graph, graph.getKmer(n), lookahead);
                        if (c >= min) {
                            if (c > cov) {
                                cov = c;
                                neighborsWithSimilarCoverage.addFirst(n);
                            }
                            else {
                                neighborsWithSimilarCoverage.addLast(n);
                            }
                        }
                    }
                    
                    if (neighborsWithSimilarCoverage.size() == 1) {
                        best = neighborsWithSimilarCoverage.peek();
                    }
                    else if (numKmers >= distance) {
                        LinkedList<String> pairedNeighbors = new LinkedList<>();
                        partner = kmers.get(numKmers-distance);
                        for (String n : fragmentNeighbors) {
                            if (graph.lookupPairedKmers(n, partner)) {
                                pairedNeighbors.add(n);
                            }
                        }
                        if (pairedNeighbors.size() == 1) {
                            best = pairedNeighbors.peek();
                        }
                    }
                    
                    if (best == null && neighborsWithSimilarCoverage.size() > 1) {
                        best = neighborsWithSimilarCoverage.getFirst();
                        if (assembledKmers.contains(best)) {
                            best = null;
                        }
                    }
                }
            }
            
            if (best == null) {
                break;
            }
            else {
                int numKmers = kmers.size();
                if (numKmers >= distance && partner == null) {
                    partner = kmers.get(numKmers-distance);
                }
                
                if (transcriptKmers.contains(best)) {
                    if (partner == null || !graph.lookupPairedKmers(best, partner)) {
                        break;
                    }
                    else {
                        String pairedKmersStr = best + partner;
                        if (usedPairs.contains(pairedKmersStr)) {
                            break;
                        }
                        else {
                            usedPairs.add(pairedKmersStr);
                        }
                    }
                }
                
                if (assembledKmers.contains(best) && (partner == null || assembledKmers.contains(partner))) {
                    break;
                }
            }
            
            kmers.add(best);
            transcriptKmers.add(best);
            neighbors = graph.getPredecessors(best);
        }
        
        Collections.reverse(kmers);
        
        return assembleString(kmers);
    }
    
    private static boolean hasFragmentDepthRight(String source, BloomFilterDeBruijnGraph graph, int depth) {
        LinkedList<LinkedList> frontier = new LinkedList<>();
        LinkedList<String> alts = new LinkedList<>();
        for (String s : graph.getSuccessors(source)) {
            if (graph.lookupFragmentKmer(s)) {
                alts.add(s);
            }
        }
        frontier.add(alts);
        
        while (!frontier.isEmpty()) {
            alts = frontier.peekLast();
            if (alts.isEmpty()) {
                frontier.removeLast();
            }
            else {
                String a = alts.pop();
                alts = new LinkedList<>();
                for (String s : graph.getSuccessors(a)) {
                    if (graph.lookupFragmentKmer(s)) {
                        alts.add(s);
                    }
                }
                frontier.add(alts);
            }

            if (frontier.size() >= depth) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean hasFragmentDepthLeft(String source, BloomFilterDeBruijnGraph graph, int depth) {
        LinkedList<LinkedList> frontier = new LinkedList<>();
        LinkedList<String> alts = new LinkedList<>();
        for (String s : graph.getPredecessors(source)) {
            if (graph.lookupFragmentKmer(s)) {
                alts.add(s);
            }
        }
        frontier.add(alts);
        
        while (!frontier.isEmpty()) {
            alts = frontier.peekLast();
            if (alts.isEmpty()) {
                frontier.removeLast();
            }
            else {
                String a = alts.pop();
                alts = new LinkedList<>();
                for (String s : graph.getPredecessors(a)) {
                    if (graph.lookupFragmentKmer(s)) {
                        alts.add(s);
                    }
                }
                frontier.add(alts);
            }

            if (frontier.size() >= depth) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean hasDepthRight(String source, BloomFilterDeBruijnGraph graph, int depth) {
        LinkedList<LinkedList> frontier = new LinkedList<>();
        LinkedList<String> alts = graph.getSuccessors(source);
        frontier.add(alts);
        
        while (!frontier.isEmpty()) {
            alts = frontier.peekLast();
            if (alts.isEmpty()) {
                frontier.removeLast();
            }
            else {
                frontier.add(graph.getSuccessors(alts.pop()));
            }

            if (frontier.size() >= depth) {
                return true;
            }
        }
        
        return false;
    }
    
    private static boolean hasDepthLeft(String source, BloomFilterDeBruijnGraph graph, int depth) {
        LinkedList<LinkedList> frontier = new LinkedList<>();
        LinkedList<String> alts = graph.getPredecessors(source);
        frontier.add(alts);
        
        while (!frontier.isEmpty()) {
            alts = frontier.peekLast();
            if (alts.isEmpty()) {
                frontier.removeLast();
            }
            else {
                frontier.add(graph.getPredecessors(alts.pop()));
            }

            if (frontier.size() >= depth) {
                return true;
            }
        }
        
        return false;
    }
    
    public static String naiveExtendWithSimpleBubblePopping(String fragment, BloomFilterDeBruijnGraph graph, int maxTipLength) {
        int k = graph.getK();
        int kMinus1 = k - 1;
        HashSet<String> fragmentKmers = new HashSet<>(2*(fragment.length()-k+1));
        kmerizeToCollection(fragment, k, fragmentKmers);
        
        LinkedList<String> neighbors = graph.getPredecessors(getFirstKmer(fragment, k));
        while (neighbors.size() > 0) {
            String bestExtension = null;
            String bestPrefix = null;
            float bestCov = 0;
            
            for (String p : neighbors) {
                String e = naiveExtendLeft(p, graph, maxTipLength, fragmentKmers);
                if (e.length() > maxTipLength) {
                    String ext = e + p;
                    
                    if (bestExtension == null) {
                        bestExtension = e + p.charAt(0);
                        bestPrefix = getFirstKmer(ext, kMinus1);
                        bestCov = getMedianKmerCoverage(graph.getKmers(ext));
                    }
                    else {
                        String myPrefix = getFirstKmer(ext, kMinus1);
                        if (bestPrefix.equals(myPrefix)) {
                            if (Math.abs(e.length() + 1 - bestExtension.length()) <= 1) {
                                float myCov = getMedianKmerCoverage(graph.getKmers(ext));
                                if (myCov > bestCov) {
                                    bestExtension = e + p.charAt(0);
                                    bestPrefix = myPrefix;
                                    bestCov = myCov;
                                }
                            }
                            else {
                                bestExtension = null;
                                bestPrefix = null;
                                bestCov = 0;
                                break;
                            }
                        }
                        else {
                            bestExtension = null;
                            bestPrefix = null;
                            bestCov = 0;
                            break;
                        }
                    }
                }
            }
            
            if (bestExtension == null) {
                break;
            }
            else {
                fragment = bestExtension + fragment;
            }
            
            /**@TODO extend to junction kmer*/
            
            neighbors = graph.getPredecessors(getFirstKmer(fragment, k));
        }
        
        neighbors = graph.getSuccessors(getLastKmer(fragment, k));
        while (neighbors.size() > 0) {
            String bestExtension = null;
            String bestPrefix = null;
            float bestCov = 0;
            
            for (String s : neighbors) {
                String e = naiveExtendRight(s, graph, maxTipLength, fragmentKmers);
                if (e.length() > maxTipLength) {
                    String ext = s + e;
                    
                    if (bestExtension == null) {
                        bestExtension = s.charAt(kMinus1) + e;
                        bestPrefix = getLastKmer(ext, kMinus1);
                        bestCov = getMedianKmerCoverage(graph.getKmers(ext));
                    }
                    else {
                        String myPrefix = getLastKmer(ext, kMinus1);
                        if (bestPrefix.equals(myPrefix)) {
                            if (Math.abs(e.length() + 1 - bestExtension.length()) <= 1) {
                                float myCov = getMedianKmerCoverage(graph.getKmers(ext));
                                if (myCov > bestCov) {
                                    bestExtension = s.charAt(kMinus1) + e;
                                    bestPrefix = myPrefix;
                                    bestCov = myCov;
                                }
                            }
                            else {
                                bestExtension = null;
                                bestPrefix = null;
                                bestCov = 0;
                                break;
                            }
                        }
                        else {
                            bestExtension = null;
                            bestPrefix = null;
                            bestCov = 0;
                            break;
                        }
                    }
                }
            }
            
            if (bestExtension == null) {
                break;
            }
            else {
                fragment = fragment + bestExtension;
            }
            
            /**@TODO extend to junction kmer*/
            
            neighbors = graph.getSuccessors(getLastKmer(fragment, k));
        }
        
        return fragment;
    }
    
    public static String naiveExtend(String fragment, BloomFilterDeBruijnGraph graph, int maxTipLength) {
        int k = graph.getK();
        HashSet<String> fragmentKmers = new HashSet<>(2*(fragment.length()-k+1));
        kmerizeToCollection(fragment, k, fragmentKmers);
        
        return naiveExtendLeft(getFirstKmer(fragment, k), graph, maxTipLength, fragmentKmers) + fragment + naiveExtendRight(getLastKmer(fragment, k), graph, maxTipLength, fragmentKmers);
    }
    
    private static String naiveExtendRight(String kmer, BloomFilterDeBruijnGraph graph, int maxTipLength, HashSet<String> terminators) {        
        StringBuilder sb = new StringBuilder(100);
        int lastBaseIndex = graph.getK()-1;
        
        LinkedList<String> neighbors = graph.getSuccessors(kmer);
        String best = kmer;
        while (!neighbors.isEmpty()) {
            String prev = best;
            
            if (neighbors.size() == 1) {
                best = neighbors.peek();
            }
            else {
                best = null;
                for (String n : neighbors) {
                    if (hasDepthRight(n, graph, maxTipLength)) {
                        if (best == null) {
                            best = n;
                        }
                        else {
                            // too many good branches
                            best = null;
                            break;
                        }
                    }
                }
            }
            
            if (best == null || terminators.contains(best)) {
                break;
            }
            
            /** look for back branches*/
            boolean hasBackBranch = false;
            for (String s : graph.getPredecessors(kmer)) {
                if (!s.equals(prev) && hasDepthLeft(s, graph, maxTipLength)) {
                    hasBackBranch = true;
                    break;
                }
            }
            if (hasBackBranch) {
                break;
            }

            sb.append(best.charAt(lastBaseIndex));
            terminators.add(best);
        }
        
        return sb.toString();
    }
    
    private static String naiveExtendLeft(String kmer, BloomFilterDeBruijnGraph graph, int maxTipLength, HashSet<String> terminators) {        
        StringBuilder sb = new StringBuilder(100);
        
        LinkedList<String> neighbors = graph.getPredecessors(kmer);
        String best = kmer;
        while (!neighbors.isEmpty()) {
            String prev = best;
            
            if (neighbors.size() == 1) {
                best = neighbors.peek();
            }
            else {
                best = null;
                for (String n : neighbors) {
                    if (hasDepthLeft(n, graph, maxTipLength)) {
                        if (best == null) {
                            best = n;
                        }
                        else {
                            // too many good branches
                            best = null;
                            break;
                        }
                    }
                }
            }
            
            if (best == null || terminators.contains(best)) {
                break;
            }
            
            /** look for back branches*/
            boolean hasBackBranch = false;
            for (String s : graph.getSuccessors(kmer)) {
                if (!s.equals(prev) && hasDepthRight(s, graph, maxTipLength)) {
                    hasBackBranch = true;
                    break;
                }
            }
            if (hasBackBranch) {
                break;
            }

            sb.append(best.charAt(0));
            terminators.add(best);
        }
        
        sb.reverse();
        
        return sb.toString();
    }
}
