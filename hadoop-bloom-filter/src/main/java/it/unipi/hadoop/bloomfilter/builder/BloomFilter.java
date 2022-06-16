package it.unipi.hadoop.bloomfilter.builder;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import it.unipi.hadoop.bloomfilter.writables.BooleanArrayWritable;
import it.unipi.hadoop.bloomfilter.writables.IntArrayWritable;
import it.unipi.hadoop.bloomfilter.linecount.LineCountMapper;
import it.unipi.hadoop.bloomfilter.linecount.LineCountReducer;
import org.apache.hadoop.io.*;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.NLineInputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.util.GenericOptionsParser;

public class BloomFilter {

	private static final int NUMBER_OF_REDUCERS = 4;
	private static final int LINES_PER_MAP = 10000;

	private static final Map<Byte, Integer> sizeOfBloomFilters = new HashMap<>();



	private static int computeNumberOfHashFunctions (double falsePositiveProbability) {
		return (int)(Math.ceil(-(Math.log(falsePositiveProbability)/(Math.log(2)))));
	}



	private static int computeSizeOfBloomFilter (double falsePositiveProbability, int numberOfInputs) {
		return (int)(Math.ceil(-(numberOfInputs * Math.log(falsePositiveProbability)) / (2 * (Math.log(2)))));
	}


	private static boolean runBloomFilterBuilder (Configuration conf,
												  int falsePositiveProbability,
	                                              Path input_path,
	                                              Path output_path)
			throws IOException, ClassNotFoundException, InterruptedException
	{
		// creation of MapReduce job
		Job job = Job.getInstance(conf, "BloomFilter");

		// Set bloomFilter parameters
		job.getConfiguration().setInt(
				"bloom.filter.number", // how many bloom filter must be created
				sizeOfBloomFilters.size()
		);
		sizeOfBloomFilters.forEach( (k, v) ->
				job.getConfiguration().setInt(
						"bloom.filter.size." + k, // size of k-th bloom filter
						v
				)
		);
		job.getConfiguration().setInt(
				"bloom.filter.hash", // how many hash functions for each bloom filter
				computeNumberOfHashFunctions(falsePositiveProbability)
		);

		// Configure whole application
		job.setJarByClass(BloomFilter.class);

		// Mapper configuration
		job.setMapperClass(BloomFilterMapper.class);
		job.setMapOutputKeyClass(ByteWritable.class);
		job.setMapOutputValueClass(IntArrayWritable.class);

		// Reducer configuration
		job.setReducerClass(BloomFilterReducer.class);
		job.setOutputKeyClass(ByteWritable.class);
		job.setOutputValueClass(BooleanArrayWritable.class);
		job.setNumReduceTasks(NUMBER_OF_REDUCERS);

		// Input configuration
		job.setInputFormatClass(NLineInputFormat.class);
		NLineInputFormat.setInputPaths(job, input_path);
		job.getConfiguration().setInt(
				"mapreduce.input.lineinputformat.linespermap",
				LINES_PER_MAP
		);

		// Output configuration
		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputPath(job, output_path);

		return job.waitForCompletion(true);
	}



	// TODO: delete this method when implemented another solution
	private static boolean runMapReduceLineCountBuilder(Configuration conf, Path input_path, Path output_path)
			throws IOException, ClassNotFoundException, InterruptedException {

		// Job configuration
		Job job = Job.getInstance(conf, "BloomFilterLineCounter");

		job.setJarByClass(BloomFilter.class);
		job.setMapperClass(LineCountMapper.class);
		job.setMapOutputKeyClass(ByteWritable.class);
		job.setMapOutputValueClass(NullWritable.class);

		job.setReducerClass(LineCountReducer.class);
		job.setOutputKeyClass(ByteWritable.class);
		job.setOutputValueClass(IntWritable.class);

		job.setInputFormatClass(NLineInputFormat.class);
		NLineInputFormat.setInputPaths(job, input_path);
		job.getConfiguration().setInt("mapreduce.input.lineinputformat.linespermap", LINES_PER_MAP);

		job.setOutputFormatClass(SequenceFileOutputFormat.class);
		SequenceFileOutputFormat.setOutputPath(job, output_path);

		return job.waitForCompletion(true);
	}



	private static void buildParameterMaps (String path, Integer p) throws IOException {
		Path inFile = new Path(path);
		ByteWritable key = new ByteWritable();
		IntWritable value = new IntWritable();

		try (SequenceFile.Reader reader =
				    new SequenceFile.Reader(
							new Configuration(),
						    SequenceFile.Reader.file(inFile)
				    )
		){
			while (reader.next(key, value)) {
				sizeOfBloomFilters.put(
						key.get(),
						computeSizeOfBloomFilter(p, value.get())
				);
			}
		}

	}




	public static void main (String[] args) throws Exception {

		Configuration conf = new Configuration();
		String[] otherArgs = new GenericOptionsParser(conf, args).getRemainingArgs();
		if (otherArgs.length < 4) {
			System.err.println("Usage: BloomFilter <false positive p> <input file> " +
					"<output file> <intermediate out file>");
			System.exit(2);
		}

		// output file from lineCount == input file for BloomFilter
		String intermediate_file = otherArgs[3];

		boolean succeded = runMapReduceLineCountBuilder(
				conf,
				new Path(otherArgs[1]),
				new Path(intermediate_file)
		);
		if (!succeded) {
			System.err.println("LineCount failed");
			System.exit(1);
		}

		// Read file
		buildParameterMaps(intermediate_file, Integer.getInteger(otherArgs[0]));

		succeded = runBloomFilterBuilder(
				conf,
				Integer.getInteger(otherArgs[0]),
				new Path(otherArgs[1]),
				new Path(otherArgs[2])
		);
		if (!succeded) {
			System.err.println("BloomFilter failed");
			System.exit(1);
		}
	}
}
