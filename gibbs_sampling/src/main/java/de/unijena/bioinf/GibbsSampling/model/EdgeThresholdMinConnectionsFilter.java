package de.unijena.bioinf.GibbsSampling.model;

import gnu.trove.list.array.TIntArrayList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class EdgeThresholdMinConnectionsFilter extends LocalEdgeFilter {
    private double basicThreshold;
    private int numberOfCandidatesWithMinConnCount;
    private int minimumConnectionCount;

    public EdgeThresholdMinConnectionsFilter(double basicThreshold, int numberOfCandidatesWithMinConnCount, int minimumConnectionCount) {
        super(Double.NaN);
        if(minimumConnectionCount < 0) {
            throw new IllegalArgumentException("min connection count must be positive");
        } else if(numberOfCandidatesWithMinConnCount<0){
            throw new IllegalArgumentException("number of candidates with minimum number of connections must be >= 0 ");
        } else {
            this.basicThreshold = Math.log(basicThreshold);
            this.numberOfCandidatesWithMinConnCount = numberOfCandidatesWithMinConnCount;
            this.minimumConnectionCount = minimumConnectionCount;
        }
    }

    public void filterEdgesAndSetThreshold(Graph graph, int candidateIdx, double[] logEdgeScores) {
        int peakIdx = graph.getPeakIdx(candidateIdx);

        double[] minThresholdPerPeak = new double[graph.numberOfCompounds()];
        for(int i = 0; i < logEdgeScores.length; ++i) {
            int peakIdxOther = graph.getPeakIdx(i);
            if(peakIdx != peakIdxOther) {
                double score = logEdgeScores[i];
                //CHANGED just add edge if <0 (at least some matching fragments)
                if (score>=0) continue;

                minThresholdPerPeak[peakIdxOther] = Math.min(minThresholdPerPeak[peakIdxOther], score);
//                //edges assigned in this 'reverse direction' (candidateIdx->i) to retrieve all of them later on
//                //after that the graph is made symmetric anyways.
//                weightedEdges.add(new WeightedEdge(candidateIdx, i, score));
            }
        }

        //negative weights are good (p-values)
        //take at least the top x edges. maybe even more if several are better than basic thres.
        Arrays.sort(minThresholdPerPeak);
        double currentThreshold;
        if(this.minimumConnectionCount >= minThresholdPerPeak.length) {
            currentThreshold = 0;//todo add pseudo???
        } else {
            //get next best weight, so that minimumConnectionCount-1 edge gets positive weight
            //if no bigger weight exists it is set to weight 0
            //using 0 might make the score to 'strong'??
            double thresholdScore = minThresholdPerPeak[minimumConnectionCount-1];
            currentThreshold = thresholdScore;
            for (int i = minimumConnectionCount; i < minThresholdPerPeak.length; i++) {
                double weight = minThresholdPerPeak[i];
                if (weight>currentThreshold){
                    currentThreshold = weight;
                    break;
                }

            }
            if (currentThreshold==thresholdScore) currentThreshold = 0;
        }


        if(currentThreshold < this.basicThreshold) {
            currentThreshold = this.basicThreshold;
        }


        for(int i = 0; i < logEdgeScores.length; ++i) {
            int peakIdxOther = graph.getPeakIdx(i);
            if(peakIdx != peakIdxOther) {
                double score = logEdgeScores[i];
                if (score>=currentThreshold) continue;

                graph.setLogWeight(candidateIdx, i, currentThreshold - score);
            }
        }


        graph.setEdgeThreshold(candidateIdx, currentThreshold);
    }

    public void setThreshold(double threshold) {
        this.basicThreshold = threshold;
    }

    public int[][] postprocessCompleteGraph(Graph graph) {
        TIntArrayList[] connectionsList = new TIntArrayList[graph.getSize()];
        
        for(int i = 0; i < graph.getSize(); ++i) {
            connectionsList[i] = new TIntArrayList(100);
        }
        
        for(int i = 0; i < graph.numberOfCompounds(); ++i) {
            int left = graph.getPeakLeftBoundary(i);
            int right = graph.getPeakRightBoundary(i);
            double[] thresholds = new double[right - left + 1];

            for(int j = left; j <= right; ++j) {
                thresholds[j - left] = graph.getEdgeThreshold(j);
            }

            Arrays.sort(thresholds);
            int pos = Math.min(this.numberOfCandidatesWithMinConnCount, thresholds.length-1);
            double t = pos == 0?thresholds[thresholds.length-1]:thresholds[pos-1];
            if(t < this.basicThreshold) {
                throw new RuntimeException("individual edge threshold must not be smaller than overall threshold");
            }
            //rather really look at the number of edges (may be less)? -> not if worth threshold is 0
            for(int j = left; j <= right; ++j) {
                double current_t = graph.getEdgeThreshold(j);
                if(current_t > t) {
                    double diff = t - current_t;
                    int[] connections1 = graph.getLogWeightConnections(j);

                    for(int k = 0; k < connections1.length; ++k) {
                        int c = connections1[k];
                        double w = graph.getLogWeight(j, c);
                        graph.setLogWeight(j, c, Math.max(0.0D, w + diff));
                    }

                    graph.setEdgeThreshold(j, t);
                }
            }
        }

        for(int i = 0; i < graph.getSize(); ++i) {
            for(int j = i + 1; j < graph.getSize(); ++j) {
                double a = graph.getLogWeight(i, j);
                double b = graph.getLogWeight(j, i);
                double max;
                if(a < b) {
                    graph.setLogWeight(i, j, b);
                    max = b;
                } else if(b < a) {
                    graph.setLogWeight(j, i, a);
                    max = a;
                } else {
                    max = a;
                }

                if(max > 0.0D) {
                    connectionsList[i].add(j);
                    connectionsList[j].add(i);
                } else if (max < 0d) {
                    throw new RuntimeException("Edge has a negative weight");
                }
            }
        }

        int[][] connections = new int[graph.getSize()][];

        for(int i = 0; i < graph.getSize(); ++i) {
            connections[i] = connectionsList[i].toArray();
        }

        return connections;
    }

}
