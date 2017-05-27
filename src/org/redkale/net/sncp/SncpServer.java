/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.redkale.net.sncp;

import java.nio.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.redkale.convert.bson.*;
import org.redkale.net.*;
import org.redkale.service.Service;
import org.redkale.util.*;

/**
 * Service Node Communicate Protocol
 *
 * <p>
 * 详情见: https://redkale.org
 *
 * @author zhangjx
 */
@SuppressWarnings("unchecked")
public class SncpServer extends Server<DLong, SncpContext, SncpRequest, SncpResponse, SncpServlet> {

    public SncpServer() {
        this(System.currentTimeMillis());
    }

    public SncpServer(long serverStartTime) {
        super(serverStartTime, "TCP", new SncpPrepareServlet());
    }

    @Override
    public void init(AnyValue config) throws Exception {
        super.init(config);
    }

    /**
     * 删除SncpFilter
     *
     * @param filterName SncpFilter名称
     *
     * @return SncpFilter
     */
    public SncpFilter removeFilter(String filterName) {
        return (SncpFilter) this.prepare.removeFilter(filterName);
    }

    /**
     * 删除SncpFilter
     *
     * @param <T>         泛型
     * @param filterClass SncpFilter类
     *
     * @return SncpFilter
     */
    public <T extends SncpFilter> T removeFilter(Class<T> filterClass) {
        return (T) this.prepare.removeFilter(filterClass);
    }

    /**
     * 添加SncpFilter
     *
     * @param filter SncpFilter
     * @param conf   AnyValue
     *
     * @return SncpServer
     */
    public SncpServer addSncpFilter(SncpFilter filter, AnyValue conf) {
        this.prepare.addFilter(filter, conf);
        return this;
    }

    /**
     * 删除SncpServlet
     *
     * @param <T>     泛型
     * @param resname String
     * @param type    Class
     *
     * @return SncpServlet
     */
    public <T extends Service> SncpServlet removeSncpServlet(String resname, Class<T> type) {
        return ((SncpPrepareServlet) this.prepare).removeSncpServlet(resname, type);
    }

    /**
     * 删除SncpServlet
     *
     * @param sncpService Service
     *
     * @return SncpServlet
     */
    public SncpServlet removeSncpServlet(Service sncpService) {
        SncpServlet servlet = null;
        String resname = Sncp.getResourceName(sncpService);
        for (Class type : Sncp.getResourceTypes(sncpService)) {
            servlet = ((SncpPrepareServlet) this.prepare).removeSncpServlet(resname, type);
        }
        return servlet;
    }

    public void addSncpServlet(Service sncpService) {
        for (Class type : Sncp.getResourceTypes(sncpService)) {
            SncpDynServlet sds = new SncpDynServlet(BsonFactory.root().getConvert(), Sncp.getResourceName(sncpService), type, sncpService);
            this.prepare.addServlet(sds, null, Sncp.getConf(sncpService));
        }
    }

    public <T extends Service> void addSncpServlet(Class<T> serviceTypeClass, String name, T service, AnyValue conf) {
        SncpDynServlet sds = new SncpDynServlet(BsonFactory.root().getConvert(), name, serviceTypeClass, service);
        this.prepare.addServlet(sds, null, conf);
    }

    public List<SncpServlet> getSncpServlets() {
        return ((SncpPrepareServlet) this.prepare).getSncpServlets();
    }

    @Override
    @SuppressWarnings("unchecked")
    protected SncpContext createContext() {
        final int port = this.address.getPort();
        AtomicLong createBufferCounter = new AtomicLong();
        AtomicLong cycleBufferCounter = new AtomicLong();
        final int rcapacity = Math.max(this.bufferCapacity, 4 * 1024);
        ObjectPool<ByteBuffer> bufferPool = new ObjectPool<>(createBufferCounter, cycleBufferCounter, this.bufferPoolSize,
            (Object... params) -> ByteBuffer.allocateDirect(rcapacity), null, (e) -> {
                if (e == null || e.isReadOnly() || e.capacity() != rcapacity) return false;
                e.clear();
                return true;
            });
        AtomicLong createResponseCounter = new AtomicLong();
        AtomicLong cycleResponseCounter = new AtomicLong();
        ObjectPool<Response> responsePool = SncpResponse.createPool(createResponseCounter, cycleResponseCounter, this.responsePoolSize, null);
        SncpContext sncpcontext = new SncpContext(this.serverStartTime, this.logger, executor, rcapacity, bufferPool, responsePool,
            this.maxbody, this.charset, this.address, this.prepare, this.readTimeoutSecond, this.writeTimeoutSecond);
        responsePool.setCreator((Object... params) -> new SncpResponse(sncpcontext, new SncpRequest(sncpcontext)));
        return sncpcontext;
    }

}
