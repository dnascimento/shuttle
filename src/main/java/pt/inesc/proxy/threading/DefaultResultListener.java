package pt.inesc.proxy.threading;


public class DefaultResultListener
        implements ResultListener {

    @Override
    public void finish(Object obj) {

    }

    @Override
    public void error(Exception ex) {
        ex.printStackTrace();
    }

}
