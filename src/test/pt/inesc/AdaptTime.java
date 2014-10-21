package pt.inesc;

public class AdaptTime {


    public static void main(String[] args) {
        long x = 16883874055519L;
        System.out.println(countDigits(x));

        int digits = countDigits(x);
        // target is 16 digits
        double diff = Math.pow(10, 16 - digits);
        x = (long) (x * diff);
        System.out.println(countDigits(x));
        System.out.println(x);
    }

    public static int countDigits(long v) {
        int i = 1;
        while (v >= 10) {
            v = v / 10;
            i++;
        }
        return i;
    }
}
