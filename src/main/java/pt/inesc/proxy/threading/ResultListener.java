package pt.inesc.proxy.threading;

/**
 * This interface imposes finish method
 * which is used to get the {@link Output} object
 * of finished task
 * 
 * @author abhishek
 * @param
 */

public interface ResultListener<T> {

    public void finish(T obj);

    public void error(Exception ex);

}
