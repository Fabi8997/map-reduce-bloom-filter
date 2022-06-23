package it.unipi.hadoop.bloomfilter.tester;

import it.unipi.hadoop.bloomfilter.writables.BooleanArrayWritable;
import it.unipi.hadoop.bloomfilter.writables.TesterGenericWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

/**
 * Class which reads the output of the builder (i.e. the bloom filter)
 * and maps it according to the key (i.e. the rating value).<br>
 * It simply distributes the bloom filters to the reducer tasks, so that
 * they can be tested.
 */
public class MapperTesterForBloomFilters
		extends Mapper<ByteWritable, BooleanArrayWritable, ByteWritable, TesterGenericWritable>
{
	// Generic wrapper for the bloom filter
	private final TesterGenericWritable object = new TesterGenericWritable();

	@Override
	public void map (ByteWritable key, BooleanArrayWritable value, Context context)
			throws IOException, InterruptedException
	{
		// Wrap the bloom filter and send it to the appropriate reducer task
		object.set(value);
		context.write(key, object);
	}

}
