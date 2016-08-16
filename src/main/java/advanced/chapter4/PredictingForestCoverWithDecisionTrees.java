package advanced.chapter4;

import java.util.Arrays;
import java.util.HashMap;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.evaluation.MulticlassMetrics;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.linalg.Vectors;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.mllib.tree.DecisionTree;
import org.apache.spark.mllib.tree.model.DecisionTreeModel;

import scala.Tuple2;

public class PredictingForestCoverWithDecisionTrees {

	public static void main(String[] args) {
		//初始化SparkConf
		SparkConf sc = new SparkConf().setMaster("local").setAppName("PredictingForestCoverWithDecisionTrees");
		System.setProperty("hadoop.home.dir", "D:/Tools/hadoop-2.6.4");
		JavaSparkContext jsc = new JavaSparkContext(sc);

		//读入数据
		JavaRDD<LabeledPoint> data = readData(jsc);
		
		//将数据分割为训练集、交叉检验集(CV)和测试集
		JavaRDD<LabeledPoint>[] splitArray = data.randomSplit(new double[]{0.8, 0.1, 0.1});
		JavaRDD<LabeledPoint> trainData = splitArray[0];
		trainData.cache();
		JavaRDD<LabeledPoint> cvData = splitArray[1];
		cvData.cache();
		JavaRDD<LabeledPoint> testData = splitArray[2];
		testData.cache();
		
		//构建DecisionTreeModel
		DecisionTreeModel model = DecisionTree.trainClassifier(trainData, 7, new HashMap<Integer, Integer>(), "gini", 4, 100);
		
		//用CV集来计算结果模型的指标
		MulticlassMetrics metrics = getMetrics(model, cvData);
		
		//查看混淆矩阵
		System.out.println(metrics.confusionMatrix());
		//查看准确度
		System.out.println(metrics.precision());
		//每个类别相对其他类别的精确度
		Arrays.asList(new Integer[]{0,1,2,3,4,5,6}).stream().forEach(cat -> System.out.println(metrics.precision(cat) + "," + metrics.recall(cat)));
		
		jsc.close();
	}

	/**
	 * 
	 * @Title: readData
	 * @Description: 读取数据，转换为包含特征值和标号的特征向量
	 * @param: @param jsc
	 * @throws:
	 */
	public static JavaRDD<LabeledPoint> readData(JavaSparkContext jsc) {
		JavaRDD<String> rawData =jsc.textFile("src/main/java/advanced/chapter4/covtype/covtype.data");
		
		JavaRDD<LabeledPoint> data = rawData.map(line -> {
			String[] values = line.split(",");
			double[] features = new double[values.length-1];
			for (int i = 0; i < values.length-1; i++) {
				features[i] = Double.parseDouble(values[i]);
			}
			Vector featureVector = Vectors.dense(features);
			Double label = (double) (Double.parseDouble(values[values.length-1]) - 1);
			return new LabeledPoint(label, featureVector);
		});
		return data;
	}
	
	/**
	 * 
	 * @Title: getMetrics
	 * @Description: 用CV集来计算结果模型的指标
	 * @param: @param model
	 * @param: @param data
	 * @throws:
	 */
	public static MulticlassMetrics getMetrics(DecisionTreeModel model, JavaRDD<LabeledPoint> data){
		JavaPairRDD<Object, Object> predictionsAndLabels = data.mapToPair(example -> {
			return new Tuple2<Object, Object>(model.predict(example.features()), example.label());
		});
		
		return new MulticlassMetrics(JavaPairRDD.toRDD(predictionsAndLabels));
	}
}