/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package rnabloom.io;

import java.io.IOException;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;
import static rnabloom.util.SeqUtils.longestSeq;
import static rnabloom.util.SeqUtils.reverseComplement;

/**
 *
 * @author gengar
 */
public class FastqFilteredSequenceIterator {
    private final Pattern seqPattern;
    private final Pattern qualPattern;
    private final String[] fastqPaths;
    private final boolean reverseComplement;
    private int fileCursor = 0;
    private FastqReader reader = null;
    private final FastqRecord record = new FastqRecord();
    
    public FastqFilteredSequenceIterator(String[] fastqPaths, Pattern seqPattern, Pattern qualPattern, boolean reverseComplement) throws IOException {
        this.seqPattern = seqPattern;
        this.qualPattern = qualPattern;
        this.fastqPaths = fastqPaths;
        this.reverseComplement = reverseComplement;
        setReader(fastqPaths[fileCursor]);
    }
    
    private void setReader(String path) throws IOException {
        if (FastqReader.isCorrectFormat(path)) {
            reader = new FastqReader(path);
        }
        else {
            throw new FileFormatException("Incompatible file format for `" + path + "`");
        }
        
        System.out.println("Parsing `" + path + "`...");
    }
    
    public boolean hasNext() throws IOException {
        boolean hasNext = reader.hasNext();
        
        if (!hasNext) {
            reader.close();
            
            if (++fileCursor >= fastqPaths.length) {
                return false;
            }
            
            setReader(fastqPaths[fileCursor]);
            
            return this.hasNext();
        }
        
        return hasNext;
    }

    public String next() throws FileFormatException, IOException {
        try {
            reader.nextWithoutName(record);
            
            String seq = longestSeq(record.seq, record.qual, seqPattern, qualPattern);

            if (reverseComplement) {
                seq = reverseComplement(seq);
            }

            return seq;
        }
        catch (NoSuchElementException e) {
            reader.close();
            
            if (++fileCursor >= fastqPaths.length) {
                throw new NoSuchElementException();
            }
            
            setReader(fastqPaths[fileCursor]);
            
            return this.next();
        }
    }
    
    
}
