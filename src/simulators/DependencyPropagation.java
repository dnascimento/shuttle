
 
import java.util.Random;

public class DependencyPropagation {
    int numberOfDataEntries;
    long numberOfRequests;
    double percentageOfDamage;
    long countAffected;
    long[] bitSet;

    public static void main(String[] args) {
        int dataEntries = 300000;
        long numberOfRequests = 3000000;
        DependencyPropagation simul = new DependencyPropagation(dataEntries, numberOfRequests, 0.01);
        simul.runTest();
        simul.print();
    }

    /**
     * 2^37 is the maximum of data entries
     * 
     * @param numberOfDataEntries
     * @param numberOfRequests
     * @param percentageOfDamage
     * @param countAffected
     */
    public DependencyPropagation(int numberOfDataEntries,
            long numberOfRequests,
            double percentageOfDamage) {
        super();
        this.numberOfDataEntries = numberOfDataEntries;
        this.numberOfRequests = numberOfRequests;
        this.percentageOfDamage = percentageOfDamage;
        this.countAffected = 0;
    }

    public void runTest() {
        // 64 bit each long
        int numberOfLongs = (int) Math.ceil(numberOfDataEntries / 8);
        bitSet = new long[numberOfLongs];

        setInitAffected();
        Random random = new Random();

        Boolean a, b, c;
        for (long i = 0; i < numberOfRequests; i++) {
            // read 3 random
            a = getBit(random.nextInt(numberOfDataEntries));
            b = getBit(random.nextInt(numberOfDataEntries));
            c = getBit(random.nextInt(numberOfDataEntries));
            // set if one is black
            if (a || b || c) {
                countAffected++;
                setBit(random.nextInt(numberOfDataEntries));
            }
        }


    }

    private void setInitAffected() {
        Random random = new Random();
        long affected = (long) (numberOfDataEntries * percentageOfDamage);
        for (int i = 0; i < affected; i++) {
            setBit(random.nextInt(numberOfDataEntries));
        }
    }

    private void setBit(int i) {
        int byteEntry = bitSet.length - (i / 64) - 1;
        int pos = i % 64;
        long mask = (((long) 1) << pos);
        bitSet[byteEntry] = bitSet[byteEntry] | mask;
    }

    private Boolean getBit(int i) {
        int byteEntry = bitSet.length - (i / 64) - 1;
        int pos = i % 64;
        long mask = (((long) 1) << pos);
        return ((bitSet[byteEntry] & mask) > 0);
    }

    public void print() {
        System.out.println("Affected: " + countAffected + ": "
                + ((double) countAffected / numberOfRequests));
        // for (int i = 0; i < numberOfDataEntries; i++) {
        // if (getBit(i)) {
        // System.out.print("1");
        // } else {
        // System.out.print("0");
        // }
        // }

    }
}
