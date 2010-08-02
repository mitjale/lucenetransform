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
        result.append("||Method");
        result.append("||");
        for (int i = 0; i < allops.size(); i++) {
            result.append(allops.get(i)).append("||");
        }
        result.append("\n");
        for (String method : methods) {
            Map<String,List<Long>> mdata = results.get(method);
            result.append("||");
            result.append(method).append("||");
            for (int i = 0; i < allops.size(); i++) {
                List<Long> data = mdata.get(allops.get(i));
                if (data != null) {
                    double[] ddata = new double[data.size()];
                    for (int j = 0; j < ddata.length; j++) {
                        ddata[j] = data.get(j);
                    }

                    result.append(String.format("%02.02f+/-%02.02f", average(ddata), stdev(ddata)));
                } else {
                    result.append("N/A");
                }
                result.append("||");
            }
            result.append("\n");

        }
        return result.toString();
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
