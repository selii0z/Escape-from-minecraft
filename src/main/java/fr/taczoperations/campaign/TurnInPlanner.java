package fr.taczoperations.campaign;

import java.util.*;

/** Allocate units to overlapping turn-in objectives without counting one unit twice.
 * A null result means the entire request is unsatisfied. No inventory mutations here. */
public final class TurnInPlanner {
    public record Demand(int amount,Set<String> accepted){
        public Demand{
            accepted=Set.copyOf(accepted);
            if(amount<=0||accepted.isEmpty())throw new IllegalArgumentException("Invalid demand");
        }
    }
    public static Map<String,Integer> plan(Map<String,Integer> stock,List<Demand> demands){
        var ids=stock.keySet().stream().sorted().toList();
        int n=ids.size(), m=demands.size(), sink=n+m+1, size=sink+1;
        if(size>4096)throw new IllegalArgumentException("Too many turn-in entries");
        long[][] residual=new long[size][size];
        long required=0;
        for(int i=0;i<n;i++){
            int count=stock.get(ids.get(i));
            if(count<0)throw new IllegalArgumentException("Negative stock");
            residual[0][i+1]=count;
            for(int j=0;j<m;j++)if(demands.get(j).accepted().contains(ids.get(i)))
                residual[i+1][n+1+j]=count;
        }
        for(int j=0;j<m;j++){
            int count=demands.get(j).amount();
            residual[n+1+j][sink]=count;required+=count;
        }
        long flow=0;
        while(flow<required){
            int[] parent=new int[size];Arrays.fill(parent,-1);parent[0]=0;
            var queue=new ArrayDeque<Integer>();queue.add(0);
            while(!queue.isEmpty()&&parent[sink]<0){
                int u=queue.remove();
                for(int v=1;v<size;v++)if(parent[v]<0&&residual[u][v]>0){parent[v]=u;queue.add(v);}
            }
            if(parent[sink]<0)return null;
            long delta=required-flow;
            for(int v=sink;v!=0;v=parent[v])delta=Math.min(delta,residual[parent[v]][v]);
            for(int v=sink;v!=0;v=parent[v]){int u=parent[v];residual[u][v]-=delta;residual[v][u]+=delta;}
            flow+=delta;
        }
        var result=new LinkedHashMap<String,Integer>();
        for(int i=0;i<n;i++){
            int used=(int)(stock.get(ids.get(i))-residual[0][i+1]);
            if(used>0)result.put(ids.get(i),used);
        }
        return Collections.unmodifiableMap(result);
    }
    private TurnInPlanner(){}
}
