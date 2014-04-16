package com.basho.riak.presto;

// /usr/local/erlang/R16B03-1/lib/erlang/lib/jinterface-1.5.8/priv/OtpErlang.jar
import com.ericsson.otp.erlang.*;

import static com.google.common.base.Preconditions.checkNotNull;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

public class Coverage {
    private final DirectConnection conn;
    private OtpErlangTuple coveragePlan;
    //private final node;

    public Coverage(DirectConnection conn){
        //this.node = riakNode;
        this.conn = conn; //checkNotNull(conn);
        this.coveragePlan = null;
    }

    @NotNull
    public void plan() {
        try{
            //conn.ping();
            this.coveragePlan = conn.getCoveragePlan(9979);
        }
        catch( java.io.IOException e ) {
          //  System.out.println(e);
        }
        catch( com.ericsson.otp.erlang.OtpAuthException e ) {
          //  System.out.println(e);
        }
        catch( com.ericsson.otp.erlang.OtpErlangExit e ) {
          //  System.out.println(e);
        }
    }

    public List<SplitTask> getSplits(){

        OtpErlangList vnodes = (OtpErlangList)this.coveragePlan.elementAt(0);
        OtpErlangObject filterVnodes = this.coveragePlan.elementAt(1);

        //System.out.println(vnodes);
        //System.out.println(filterVnodes);

        List<SplitTask> l = new ArrayList<SplitTask>();

        for(OtpErlangObject obj : vnodes)
        {

            // {Index, Node}
            OtpErlangTuple vnode = (OtpErlangTuple)obj;
            OtpErlangObject index = vnode.elementAt(0);
            String nodeName = ((OtpErlangAtom)vnode.elementAt(1)).atomValue();
            OtpErlangObject[] a = {vnode, filterVnodes};
            OtpErlangTuple t = new OtpErlangTuple(a);

            SplitTask task = new SplitTask(nodeName, t);
            l.add(task);
        }

        return l;
    }

    public String toString() {
        return coveragePlan.toString();
    }

    public void run()
        throws java.io.IOException, OtpErlangExit, OtpAuthException
    {
//        byte[] b = "foobartable".getBytes();
//        for(OtpErlangObject split : this.splits) {
//            OtpErlangTuple t = (OtpErlangTuple)split;
//            OtpErlangList r = conn.processSplits(b, t);
//            System.out.println(r);
//        }

    }
}