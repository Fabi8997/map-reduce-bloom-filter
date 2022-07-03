import argparse
import mmh3
from pyspark import SparkContext
from typing import Tuple

import bloomfilters_util as util


def parse_arguments() -> Tuple[float, str, str, str, str]:
    """
    Parse the command line input arguments
    :return: parsed arguments
    """
    parser = argparse.ArgumentParser()
    parser.add_argument('false_positive_prob', type=float, help='probability of false positives')
    parser.add_argument('dataset_input_file', type=str, help='path of the dataset')
    parser.add_argument('bloom_filters_input_file', type=str, help='path to the output of the bloom filters builder')
    parser.add_argument('linecount_file', type=str, help='path of the linecount output file')
    parser.add_argument('output_file', type=str, help='path of the output file')
    args = parser.parse_args()
    
    return args.false_positive_prob, args.dataset_input_file, \
           args.bloom_filters_input_file, args.linecount_file, \
           args.output_file


def check_false_positive(indexes: list, bloom_filter: list) -> int:
    """
    Iterate the array of the hash values (i.e. the position indexes in the bloom filter)
    to check the current value.
    If at least one value is not set, then the sample is not a false positive

    :param indexes: list of indexes to check if set to True in the bloom filter
    :param bloom_filter: list of a bloom filter created in the builder

    :return: 1 or 0 if the movie to check is a false positive (1) or not (0)
    """
    for i in indexes:
        if not bloom_filter[i]:
            return 0
    
    return 1


def sum_elem_lists(list1: list, list2: list) -> list:
    """
    Sum each element of the second list to the respective element of the first list

    :param list1: first list
    :param list2: second list

    :return: computed list
    """
    list1 = [x + y for x, y in zip(list1, list2)]
    return list1


def main():
    false_positive_prob, dataset_input_file, bloom_filters_file, linecount_file, output_file = parse_arguments()
    
    sc = SparkContext(appName="SPARK_BLOOM_FILTERS_TESTER", master="yarn")
    
    # Add Python dependencies to Spark application
    sc.addPyFile("./bloomfilters_util.py")
    sc.addPyFile(mmh3.__file__)
    
    # Create broadcast variables so that each worker node can retrieve
    # how many hash functions must be used and the size of each bloom filter
    broadcast_hash_function_number = sc.broadcast(
        util.compute_number_of_hash_functions(false_positive_prob)
    )
    broadcast_size_of_bloom_filters = sc.broadcast(
        util.get_size_of_bloom_filters(sc, linecount_file, false_positive_prob)
    )
    print()
    print("Number of hash functions: " + str(broadcast_hash_function_number.value))
    print("Size of bloom filters: " + str(broadcast_size_of_bloom_filters.value))
    print()
    
    # Read bloom filters from the output file of the builder and distribute to the worker nodes
    bloom_filters = sc.broadcast(dict(sc.pickleFile(bloom_filters_file).collect()))
    
    """
        1. read tester dataset
        2. map: parse each line of the dataset to extract the rating and a list of indexes to set True in the
                bloom filter (generated by computing the hash values from the movie's id).
                Output the key-value tuple (rating, [indexes]).
        3. map: check if the movie is a false positive and count the iteration (rating, [false_positive, 1])
        4. reduceByKey: group by rating and perform an element-wise addition to compute the number of false positive
                        and the total number of movie tested
        5. map: take (rating, [#false_positive, #movies]) and compute the false positive percentage
        6. save the results (rating, [#false_positive, #movies, %false_positive]) as a text file
    """
    sc.textFile(dataset_input_file) \
        .map(lambda line:
             util.create_pair_rating_indexes(line,
                                             broadcast_size_of_bloom_filters.value,
                                             broadcast_hash_function_number.value)
             ) \
        .map(lambda rating_indexes: (rating_indexes[0],
                                     [check_false_positive(rating_indexes[1],
                                                           bloom_filters.value[rating_indexes[0]]),
                                      1]
                                     )
             ) \
        .reduceByKey(sum_elem_lists) \
        .map(lambda rating_counters: (rating_counters[0], (rating_counters[1][0],
                                                           rating_counters[1][1],
                                                           rating_counters[1][0] / rating_counters[1][1])
                                      )
             ) \
        .saveAsTextFile(output_file)
    
    print("\n\nBLOOM FILTERS TESTER COMPLETED\n\n")


if __name__ == '__main__':
    main()
