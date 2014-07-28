/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.apache.lucene.store.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 * @author mile4386
 */
public class TimeCollector {

    private Map<String, Map<String, List<Long>>> results;
    private List<String> ops;
    private List<String> methods;

    public TimeCollector() {
        this.results = new HashMap<String, Map<String, List<Long>>>();
        this.ops = new ArrayList<String>();
        this.methods = new ArrayList<String>();
    }

    public void addMesurement(String method, String ops, long pTime) {
        Map<String, List<Long>> methodMap = results.get(method);
        if (methodMap == null) {
            methodMap = new HashMap<String, List<Long>>();
            results.put(method, methodMap);
        }
        List<Long> data = methodMap.get(ops);
        if (data == null) {
            data = new ArrayList<Long>();
            methodMap.put(ops, data);
        }
        data.add(pTime);
        if (!this.ops.contains(ops)) {
            this.ops.add(ops);
        }
        if (!this.methods.contains(method)) {
            this.methods.add(method);
        }
    }



    public String toString() {
        StringBuilder result = new StringBuilder();
        List<String> allops = ops;
        result.append("<table border=\"1\">");
        result.append("<tr><th>Method</th>");
        for (int i = 0; i < allops.size(); i++) {
            result.append("<td>").append(allops.get(i)).append("</td>");
        }
        result.append("</tr>");
        Map<String,List<Long>> reference = null;
        if (!methods.isEmpty()) {
            reference = results.get(methods.get(0));
        }

        for (String method : methods) {
            Map<String,List<Long>> mdata = results.get(method);
            result.append("<tr><td>").append(method).append("</td>");            
            for (int i = 0; i < allops.size(); i++) {
                List<Long> data = mdata.get(allops.get(i));
                if (data != null) {
                    double[] ddata = toArray(data);
                    String color = null;
                    String refData = null;
                    if (reference!=null) {
                        List<Long> rdata = reference.get(allops.get(i));
                        if (rdata!=null) {
                            double[] rddata = toArray(rdata);
                            double ravg = average(rddata);
                            double rstd = stdev(rddata);
                            double avg = average(ddata);
                            double std = stdev(ddata);
                            double pratio = avg*100/ravg;
                            if (pratio>200) {
                                color="red";
                            }
                            if (pratio<50) {
                                color = "green";
                            }
                            result.append("<td");
                            if (color!=null) {
                                result.append(" bgcolor=\"").append(color).append("\"");
                            }
                            result.append(">");
                            result.append(String.format("%02.02f+/-%02.02f ", avg, std));
                            result.append(String.format("%02.02f%%+/-%02.02f%%", pratio , pratio*((rstd/ravg)+(std/avg))));
                            result.append("</td>");
                        }
                    } else {
                        result.append(String.format("<td>%02.02f+/-%02.02f</td>", average(ddata), stdev(ddata)));
                    }
                } else {
                    result.append("<td>N/A</td>");
                }                
            }
            result.append("</tr>");

        }
        result.append("</table>");
        return result.toString();
    }

    private double[] toArray(List<Long> data) {
        double[] ddata = new double[data.size()];
        for (int j = 0; j < ddata.length; j++) {
            ddata[j] = data.get(j);
        }
        return ddata;
    }

    public void clear() {
        results.clear();
    }

    private double stdev(double[] data) {
        final int n = data.length;
        if (n < 2) {
            return Double.NaN;
        }
        double avg = data[0];
        double sum = 0;
        for (int i = 1; i < data.length; i++) {
            double newavg = avg + (data[i] - avg) / (i + 1);
            sum += (data[i] - avg) * (data[i] - newavg);
            avg = newavg;
        }
        return Math.sqrt(sum / (n - 1));
    }

    private static double average(double[] ddata) {
        double average = 0;
        for (int i = 0; i < ddata.length; i++) {
            average += ddata[i];
        }
        return average / ddata.length;
    }
}
