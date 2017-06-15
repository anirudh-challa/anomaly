package org.linqs.psl.example.easycc;

import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabasePopulator;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.groovy.PSLModel;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.utils.dataloading.InserterUtils;
import org.linqs.psl.utils.evaluation.printing.AtomPrintStream;
import org.linqs.psl.utils.evaluation.printing.DefaultAtomPrintStream;
import org.linqs.psl.utils.evaluation.statistics.ContinuousPredictionComparator;
import org.linqs.psl.utils.evaluation.statistics.DiscretePredictionComparator;
import org.linqs.psl.utils.evaluation.statistics.DiscretePredictionStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import groovy.time.TimeCategory;
import java.nio.file.Paths;

/**
 * A simple Collective Classification example that mirrors the example for the
 * PSL command line tool.
 * This PSL program uses social relationships to determine where people live.
 * It optionally uses a functional constraint that specified that a person
 * can only live in one location.
 *
 * @author Anirudh C
 */
public class EasyCC {
	private static final String PARTITION_OBSERVATIONS = "observations";
	private static final String PARTITION_TARGETS = "targets";
	private static final String PARTITION_TRUTH = "truth";

	private Logger log;
	private DataStore ds;
	private PSLConfig config;
	private PSLModel model;

	/**
	 * Class containing options for configuring the PSL program
	 */
	private class PSLConfig {
		public ConfigBundle cb;

		public String experimentName;
		public String dbPath;
		public String dataPath;
		public String outputPath;

		public boolean sqPotentials = true;
		public Map weightMap = [
				"Knows":10,
				"Prior":2
		];
		public boolean useFunctionalConstraint = false;
		public boolean useFunctionalRule = false;

		public PSLConfig(ConfigBundle cb){
			this.cb = cb;

			this.experimentName = cb.getString('experiment.name', 'default');
			this.dbPath = cb.getString('experiment.dbpath', '/tmp');
			this.dataPath = cb.getString('experiment.data.path', ".");
			this.outputPath = cb.getString('experiment.output.outputdir', ".");

			this.weightMap["Knows"] = cb.getInteger('model.weights.knows', weightMap["Knows"]);
			this.weightMap["Prior"] = cb.getInteger('model.weights.prior', weightMap["Prior"]);
			this.useFunctionalConstraint = cb.getBoolean('model.constraints.functional', false);
			this.useFunctionalRule = cb.getBoolean('model.rules.functional', false);
		}
	}

	public EasyCC(ConfigBundle cb) {
		log = LoggerFactory.getLogger(this.class);
		config = new PSLConfig(cb);
		ds = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, Paths.get(config.dbPath, 'easycc').toString(), true), cb);
		model = new PSLModel(this, ds);
	}

	/**
	 * Defines the logical predicates used by this program
	 */
	private void definePredicates() {
		
		model.add predicate: "product_quality",	types: [ConstantType.UniqueID];
		model.add predicate: "user_credibility",	types: [ConstantType.UniqueID];
		model.add predicate: "ratings",	types: [ConstantType.UniqueID , ConstantType.UniqueID ];
		model.add predicate: "rated",	types: [ConstantType.UniqueID , ConstantType.UniqueID ];
	}

	/**
	 * Defines the rules used to infer unknown variables in the PSL program
	 */
	private void defineRules() {
		log.info("Defining model rules");

		

		
		model.add(
			rule: (rated (U,P) & user_credibility(U) & ratings (U,P)) >>  product_quality (P),
			squared: config.sqPotentials,
			weight : 5
		);

		model.add(
			rule: (rated (U,P) & product_quality (P)  & ratings (U,P)) >>  user_credibility(U),
			squared:config.sqPotentials,
			weight: 5
		);

		model.add(
			rule: (rated (U,P) & user_credibility(U) & ~ratings (U,P)) >>  ~product_quality (P),
			squared: config.sqPotentials,
			weight : 5
		);

	
		model.add(
			rule: (rated (U,P) & product_quality (P)  & ~ratings (U,P)) >>  ~user_credibility(U),
			squared:config.sqPotentials,
			weight: 5
		);
	

		

		/*
		model.add(
			rule: ( ~user_credibility(U) & ratings (U,P)) >>  ~product_quality (P),
			squared: config.sqPotentials,
			weight : 5
		);
		
		
		model.add(
			rule: (~product_quality (P)  & ratings (U,P)) >>  ~user_credibility(U),
			squared:config.sqPotentials,
			weight: 5
		);
		
		*/
		
		model.add(
			//rule: user_credibility(U),
			rule: "user_credibility(U) = 1",
			squared:config.sqPotentials,
			weight: 1 
		);

		
		model.add(
			//rule: product_quality (P),
			rule: "product_quality(P) = 1",			
			squared:config.sqPotentials,
			weight: 1
		);
		
		
		log.debug("model: {}", model);
	}

	/**
	 * Loads the evidence, inference targets, and evaluation data into the DataStore
	 */
	private void loadData(Partition obsPartition, Partition targetsPartition, Partition truthPartition) {
		log.info("Loading data into database");

		Inserter inserter = ds.getInserter(ratings, obsPartition);
		InserterUtils.loadDelimitedDataTruth(inserter, Paths.get(config.dataPath, "toy1.txt").toString(),",");

		inserter = ds.getInserter(rated, obsPartition);
		InserterUtils.loadDelimitedDataTruth(inserter, Paths.get(config.dataPath, "rated.txt").toString(),",");

		inserter = ds.getInserter(user_credibility, targetsPartition);
		InserterUtils.loadDelimitedData(inserter, Paths.get(config.dataPath, "users.txt").toString(),",");

		inserter = ds.getInserter(product_quality, targetsPartition);
		InserterUtils.loadDelimitedData(inserter, Paths.get(config.dataPath, "products.txt").toString(),",");

		

	}

	/**
	 * Performs inference using the defined model and evidence, storing results in DataStore
	 */
	private void runInference(Partition obsPartition, Partition targetsPartition) {
		log.info("Starting inference");

		Date infStart = new Date();
		HashSet closed = new HashSet<StandardPredicate>([ratings]);
		Database inferDB = ds.getDatabase(targetsPartition, closed, obsPartition);
		MPEInference mpe = new MPEInference(model, inferDB, config.cb);
		mpe.mpeInference();

		inferDB.close();
		mpe.close();

		log.info("Finished inference in {}", TimeCategory.minus(new Date(), infStart));
	}

	/**
	 * Writes the inference outputs to a file
	 */
	private void writeOutput(Partition targetsPartition) {
		Database resultsDB = ds.getDatabase(targetsPartition);
		PrintStream ps = new PrintStream(new File(Paths.get(config.outputPath, "output.txt").toString()));
		AtomPrintStream aps = new DefaultAtomPrintStream(ps);
		Set atomSet = Queries.getAllAtoms(resultsDB, user_credibility);
		for (Atom a : atomSet) {
			aps.printAtom(a);
		}

		atomSet = Queries.getAllAtoms(resultsDB, product_quality);
		for (Atom a : atomSet) {
			aps.printAtom(a);
		}

		aps.close();
		ps.close();
		resultsDB.close();
	}

	/**
	 * Evaluates the results of inference versus expected truth values
	 */
	 /*
	private void evalResults(Partition targetsPartition, Partition truthPartition) {
		Database resultsDB = ds.getDatabase(targetsPartition, [Lives] as Set);
		Database truthDB = ds.getDatabase(truthPartition, [Lives] as Set);
		DiscretePredictionComparator dpc = new DiscretePredictionComparator(resultsDB);
		dpc.setBaseline(truthDB);
		DiscretePredictionStatistics stats = dpc.compare(Lives);
		log.info(
				"Stats: precision {}, recall {}",
				stats.getPrecision(DiscretePredictionStatistics.BinaryClass.POSITIVE),
				stats.getRecall(DiscretePredictionStatistics.BinaryClass.POSITIVE));

		resultsDB.close();
		truthDB.close();
	}

	*/
	/**
	 * Runs the PSL program using configure options - defines a model, loads data,
	 * performs inferences, writes output to files, evaluates results
	 */
	public void run() {
		log.info("Running experiment {}", config.experimentName);

		Partition obsPartition = ds.getPartition(PARTITION_OBSERVATIONS);
		Partition targetsPartition = ds.getPartition(PARTITION_TARGETS);
		Partition truthPartition = ds.getPartition(PARTITION_TRUTH);

		definePredicates();
		defineRules();
		loadData(obsPartition, targetsPartition, truthPartition);
		runInference(obsPartition, targetsPartition);
		writeOutput(targetsPartition);
		//evalResults(targetsPartition, truthPartition);

		ds.close();
	}

	/**
	 * Populates the ConfigBundle for this PSL program using arguments specified on
	 * the command line
	 * @param args - Command line arguments supplied during program invocation
	 * @return ConfigBundle with the appropriate properties set
	 */
	public static ConfigBundle populateConfigBundle(String[] args) {
		ConfigBundle cb = ConfigManager.getManager().getBundle("easycc");
		if (args.length > 0) {
			cb.setProperty('experiment.data.path', args[0]);
		}
		return cb;
	}

	/**
	 * Runs the PSL program from the command line with specified arguments
	 * @param args - Arguments for program options
	 */
	public static void main(String[] args){
		ConfigBundle cb = populateConfigBundle(args);
		EasyCC cc = new EasyCC(cb);
		cc.run();
	}
}
