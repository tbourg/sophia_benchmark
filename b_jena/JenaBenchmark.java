import java.io.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.*;
import org.apache.jena.vocabulary.*;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.jena.riot.*;
import org.apache.jena.graph.*;
import org.apache.jena.riot.lang.PipedRDFIterator;
import org.apache.jena.riot.lang.PipedRDFStream;
import org.apache.jena.riot.lang.PipedTriplesStream;


public class JenaBenchmark {
    public static void main(String[] args) {

        if (args[0].equals("parse")) {
            benchmark_parse(args);
        } else if (args[0].equals("query")) {
            benchmark_query(1, args);
        } else if (args[0].equals("query2")) {
            benchmark_query(2, args);
        } else if (args[0].equals("test")) {
            benchmark_test(args);
        } else {
            System.err.println("Unrecognized benchmark name");
        }
    }

    public static void benchmark_parse(String[] args) {
        System.err.println("benchmark: parse");
	
	    
        long t0, t1;
	double time_load;
        t0 = System.nanoTime();
	    InputStream in = FileManager.get().open(args[1]);
		final PipedRDFIterator<Triple> iter = new PipedRDFIterator<Triple>();
		final PipedRDFStream<Triple> tripleStream = new PipedTriplesStream(iter);
		final ExecutorService executor = Executors.newSingleThreadExecutor();
		final Runnable parser = new Runnable() {
			@Override
			public void run() {
				// Call the parsing process.
				try {
					RDFDataMgr.parse(tripleStream, in, Lang.TTL);
				} catch (final Exception e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}
		};
		executor.submit(parser);
		while (iter.hasNext()) {
			final Triple triple = iter.next();
			// parseTriple(triple);
		}
		iter.close();
	t1 = System.nanoTime();
        time_load = (t1-t0)/1e9;
        System.out.println(time_load);
    }

    public static void benchmark_query(int queryNum, String[] args) {
        // writes 3 numbers:
        // - time (in s) to load the NT file into an in-memory graph
        // - memory (in kB) allocated for creating and loading graph
        // - time (in s) to retrieve the first triple matching (* rdf:type *)
        // - time (in s) to retrieve all the remaining matching triples
        System.err.println("benchmark: query");

        double time_load, time_first = 0, time_rest;
        long mem_graph;
        
        long m0, m1;
        m0 = get_memory_footprint();
        Model model = ModelFactory.createDefaultModel();

        long t0, t1;
        t0 = System.nanoTime();
        InputStream in = FileManager.get().open(args[1]);
        model.read(in, null, "N-TRIPLES");
        t1 = System.nanoTime();
        m1 = get_memory_footprint();
        time_load = (t1-t0)/1e9;
        mem_graph = m1-m0;

        t0 = System.nanoTime();
        Resource personClass = model.createResource("http://dbpedia.org/ontology/Person");
        Resource vincent = model.createResource("http://dbpedia.org/resource/Vincent_Descombes_Sevoie");
        SimpleSelector selector;
        if (queryNum == 1) {
            selector = new SimpleSelector(null, RDF.type, personClass);
        } else {// if (queryNum == 2) {
            Resource no_obj = null;
            selector = new SimpleSelector(vincent, null, no_obj);
        }
        StmtIterator results = model.listStatements(selector);
        long nb_stmts = 0;
        while (results.hasNext()) {
            Statement s = results.next();
            nb_stmts += 1;
            if (nb_stmts == 1) {
                t1 = System.nanoTime();
                time_first = (t1-t0)/1e9;
                t0 = System.nanoTime();
            }
        }
        t1 = System.nanoTime();
        time_rest = (t1-t0)/1e9;

        System.err.println("parsed: " + model.size() + " statements");
        System.err.println("matched: " + nb_stmts + " statements");
        System.out.println(time_load + "," + mem_graph + "," + time_first + "," + time_rest);
    }

    public static void benchmark_test(String[] args) {
        System.err.println("benchmark: test");
    }

    public static long get_memory_footprint() {
        try {
            String filename = "/proc/" + ProcessHandle.current().pid() + "/status";
            BufferedReader br;
            br = new BufferedReader(
                    new InputStreamReader(
                        new FileInputStream(filename)));
            String vmsize = br.lines()
                .filter(line -> line.matches("VmSize.*"))
                .findFirst()
                .get()
                .replaceAll("VmSize:\\h*", "")
                .replaceAll(" *kB", "");
            return Long.parseLong(vmsize);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

}
