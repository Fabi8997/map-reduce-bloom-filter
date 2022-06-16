package it.unipi.hadoop.bloomfilter.linecount;

import org.apache.hadoop.io.*;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public class LineCountMapper extends Mapper<LongWritable, Text, ByteWritable, NullWritable> {

	private final ByteWritable RANKING = new ByteWritable((byte) 0);
	private static final NullWritable NULL = NullWritable.get();

	@Override
	public void map (LongWritable key, Text value, Context context)
			throws IOException, InterruptedException
	{
		// TODO Parse input to get ranking @Fabiano
		context.write(RANKING, NULL);
	}
}
