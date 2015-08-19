package org.broadinstitute.dropseqrna.utils.readiterators;

import htsjdk.samtools.SAMRecord;
import htsjdk.samtools.util.CloseableIterator;
import org.broadinstitute.dropseqrna.barnyard.Utils;
import org.broadinstitute.dropseqrna.utils.CountChangingIteratorWrapper;

import java.util.Collection;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

//TODO: Better name?
public class UmiIteratorWrapper extends CountChangingIteratorWrapper<SAMRecord> {
	
	private String cellBarcodeTag;
	private Set<String> cellBarcodeList;
	private String geneExonTag;
	private String strandTag;
	private int readMQ;
	private boolean assignReadsToAllGenes;
	private boolean useStrandInfo;
	private Random rand = new Random();
	
	/**
	 * Filters and copies reads for generating UMICollections.
	 * Only accepts reads that where the read cell barcode matches a barcode in the list (if not null)
	 * Reads that are marked as secondary or supplementary are rejected
	 * Filters reads based on read map quality, removing reads below that quality
	 * Optionally filters reads where the annotated gene and the strand of the read don't match, or can clone a read and return it multiple times
	 * if the read maps to more than one gene and <assignReadsToAllGenes> is true.
	 * @param cellBarcodeTag The cell barcode BAM tag
	 * @param cellBarcodeList A list of cell barcodes, or null to ignore.  If populated, reads where the cellBarcodeTag matches one of these Strings will be retained
	 * @param geneExonTag The gene/exon tag.
	 * @param strandTag The strand tag
	 * @param readMQ The minimum map quality of a read to be retained.
	 * @param assignReadsToAllGenes Clone SAMRecord for each gene
	 * @param useStrandInfo Only queue SAMRecord if gene strand agrees with read strand.
	 */
	public UmiIteratorWrapper(CloseableIterator<SAMRecord> underlyingIterator,
                              String cellBarcodeTag,
                              Collection<String> cellBarcodeList,
                              String geneExonTag,
                              String strandTag,
                              int readMQ,
                              boolean assignReadsToAllGenes,
                              boolean useStrandInfo) {
        super(underlyingIterator);
		this.cellBarcodeTag = cellBarcodeTag;
		this.cellBarcodeList = new HashSet<String>(cellBarcodeList);
		this.geneExonTag=geneExonTag;
		this.strandTag= strandTag;
		this.readMQ = readMQ;
		this.assignReadsToAllGenes = assignReadsToAllGenes;
		this.useStrandInfo = useStrandInfo;
	}

    @Override
    protected void processRecord(SAMRecord r) {
		String cellBC=r.getStringAttribute(cellBarcodeTag);
		String geneList = r.getStringAttribute(this.geneExonTag);
		
		// if there are cell barcodes to filter on, and this read's cell barcode isn't one of them, then move on to the next read;
		if (this.cellBarcodeList!=null && !cellBarcodeList.contains(cellBC)) {
			return;
		}
		// if the read doesn't pass map quality, etc, more on.
		if (r.isSecondaryOrSupplementary() || r.getMappingQuality()<this.readMQ || geneList==null) {
			return;
		}
		
		// there's at least one good copy of the read.  Does the read match on strand/gene, or is it assigned to multiple genes?
		String [] genes = geneList.split(",");
		String [] strands = null;
		
		if (this.useStrandInfo) {
			strands = r.getStringAttribute(strandTag).split(",");
		}
		
		if (this.assignReadsToAllGenes) {
			for (int i=0; i<genes.length; i++) {
				String g = genes[i];
				SAMRecord rr = Utils.getClone(r);
				rr.setAttribute(geneExonTag, g);
				
				// if you use strand info, then the gene has to match the read strand for the read to be added.
				if (useStrandInfo) {
					String geneStrand = strands[i];
					String readStrandString = Utils.strandToString(!r.getReadNegativeStrandFlag());
					if (geneStrand.equals(readStrandString)) {
                        queueRecordForOutput(rr);
					} else {
						// rejected read
					}
				} else { // if you don't use strand info, add the read to all genes.
                    queueRecordForOutput(rr);
				}
			}
		} 
		else {
			// pick a random read.
			int randomNum = rand.nextInt((genes.length-1) + 1);
			String g = genes[randomNum];
			r.setAttribute(geneExonTag, g);
            queueRecordForOutput(r);
		}
	}
}