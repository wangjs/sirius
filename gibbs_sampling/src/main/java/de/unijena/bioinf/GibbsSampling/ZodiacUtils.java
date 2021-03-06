package de.unijena.bioinf.GibbsSampling;

import de.unijena.bioinf.ChemistryBase.chem.InChI;
import de.unijena.bioinf.ChemistryBase.chem.MolecularFormula;
import de.unijena.bioinf.ChemistryBase.chem.PeriodicTable;
import de.unijena.bioinf.ChemistryBase.chem.PrecursorIonType;
import de.unijena.bioinf.ChemistryBase.ms.Ms2Experiment;
import de.unijena.bioinf.GibbsSampling.model.*;
import de.unijena.bioinf.babelms.MsExperimentParser;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TCharSet;
import gnu.trove.set.hash.TCharHashSet;
import org.openscience.cdk.DefaultChemObjectBuilder;
import org.openscience.cdk.exception.CDKException;
import org.openscience.cdk.interfaces.IAtomContainer;
import org.openscience.cdk.smiles.SmilesParser;
import org.openscience.cdk.tools.manipulator.MolecularFormulaManipulator;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ZodiacUtils {
    private static final String[] reactionStringsMyCompoundID = new String[]{"H2", "CH2", "NH", "O", "NH3", "H2O", "CO", "C2H4", "C2H2O", "CO2", "C2H3NO", "SO3", "HPO3", "C4H3N3", "C4H2N2O", "C3H5NOS", "C2H5NO2S", "C5H4N2O", "C3H5NO2S", "C5H8O4", "C5H3N5", "C7H13NO2", "C5H7NO3S", "C6H10O5", "C6H8O6", "C10H12N2O4", "C9H11N3O4", "C9H10N2O5", "C16H30O", "C6H11O8P", "C10H11N5O3", "C10H11N5O4", "C10H15N3O5S", "C10H15N3O6S", "C12H20O10", "C18H30O15"};
    private static final String[] reactionStringsRogers = new String[]{"C10H11N5O3", "C10H11N5O4", "C10H12N2O4", "C10H12N5O6P", "C10H12N5O7P", "C10H13N2O7P", "C10H13N5O10P2", "C10H13N5O9P2", "C10H14N2O10P2", "C10H14N2O2S", "C10H15N2O3S", "C10H15N3O5S", "C11H10N2O", "C12H20O11", "C16H30O", "C18H30O15", "C21H33N7O15P3S", "C21H34N7O16P3S", "C2H2", "C2H2O", "C2H3NO", "C2H3O2", "C2H4", "C2O2", "C3H2O3", "C3H5NO", "C3H5NO2", "C3H5NOS", "C3H5O", "C4H3N2O2", "C4H4N3O", "C4H4O2", "C4H5NO3", "C4H6N2O2", "C4H7NO2", "C5H4N5", "C5H4N5O", "C5H5N2O2", "C5H7", "C5H7NO", "C5H7NO3", "C5H8N2O2", "C5H8O4", "C5H9NO", "C5H9NOS", "C6H10N2O3S2", "C6H10O5", "C6H10O6", "C6H11NO", "C6H11O8P", "C6H12N2O", "C6H12N4O", "C6H7N3O", "C6H8O6", "C8H8NO5P", "C9H10N2O5", "C9H11N2O8P", "C9H12N2O11P2", "C9H12N3O7P", "C9H13N3O10P2", "C9H9NO", "C9H9NO2", "CH2", "CH2ON", "CH3N2O", "CHO2", "CO", "CO2", "H2", "H2O", "H3O6P2", "HPO3", "N", "NH", "NH2", "O", "P", "PP", "SO3"};

    /**
     *
     * @param stepsize up to stepsize many {@link Reaction}s can be combined to a new {@link Reaction}
     * @return
     */
    public static Reaction[] parseReactions(int stepsize){
        Set<Reaction> reactionsSet = new HashSet<>();
        for (int i = 0; i < reactionStringsMyCompoundID.length; i++) {
            reactionsSet.add(parseReactionString(reactionStringsMyCompoundID[i]));
        }
        for (int i = 0; i < reactionStringsRogers.length; i++) {
            reactionsSet.add(parseReactionString(reactionStringsRogers[i]));
        }

        if (stepsize>1){

            //convert to combined reaction for equal testing
            Set<Reaction> reactionsSetCombined = new HashSet<>();
            for (Reaction reaction : reactionsSet) {
                reactionsSetCombined.add(new CombinedReaction(reaction));
            }

            Set<Reaction> reactionsSetAll = new HashSet<>(reactionsSetCombined);
            Set<Reaction> reactionsSet1 = new HashSet<>(reactionsSetCombined);
            Set<Reaction> singleReactionsSet = new HashSet<>(reactionsSetCombined);
            int i = 1;
            while (i++<stepsize){
                Set<Reaction> reactionSet2 = new HashSet<>();
                for (Reaction reaction1 : reactionsSet1) {
                    for (Reaction reaction2 : singleReactionsSet) {
                        CombinedReaction combinedReaction = new CombinedReaction(reaction1, reaction2);

                        //don't use empty or negative reactions (duplicates)
                        if (!combinedReaction.netChange().equals(MolecularFormula.emptyFormula()) && combinedReaction.netChange().getMass()>0){
                            reactionSet2.add(combinedReaction);
                        }
                        combinedReaction = new CombinedReaction(reaction1, reaction2.negate());

                        //don't use empty or negative reactions (duplicates)
                        if (!combinedReaction.netChange().equals(MolecularFormula.emptyFormula()) && combinedReaction.netChange().getMass()>0){
                            reactionSet2.add(combinedReaction);
                        }
                    }
                }

                //todo does not remove dublicates!!
                reactionsSet1 = new HashSet<>();
                int duplicate = 0;
                for (Reaction reaction : reactionSet2) {
                    if (!reactionsSetAll.contains(reaction)) reactionsSet1.add(reaction);
                    else {
                        duplicate++;
                    }
                }
//                System.out.println("duplicate "+duplicate+ " new "+reactionsSet1.size());
                reactionsSetAll.addAll(reactionsSet1);
            }
            reactionsSet = reactionsSetAll;
        }
        Reaction[] reactions = reactionsSet.toArray(new Reaction[0]);
        Set<MolecularFormula> formulas = new HashSet<>();
        for (Reaction reaction : reactions) {
            formulas.add(reaction.netChange());
        }
//        System.out.println("formulas: "+formulas.size());
//        System.out.println("reactions: "+reactions.length);
        return reactions;

    }

    private static Reaction parseReactionString(String string) {
        String[] reactants = string.split("->");
        if(reactants.length == 1) {
            return new SimpleReaction(MolecularFormula.parse(reactants[0]));
        } else if(reactants.length == 2) {
            return new Transformation(MolecularFormula.parse(reactants[0]), MolecularFormula.parse(reactants[1]));
        } else {
            throw new RuntimeException("Error parsing reaction");
        }
    }

    public static List<LibraryHit> parseLibraryHits(Path libraryHitsPath, Path mgfFile, Logger logger) throws IOException {
        try {
            List<String> featureIDs = new ArrayList<>();
            try(BufferedReader reader = Files.newBufferedReader(mgfFile)){
                String line;
                String lastID = null;
                while ((line=reader.readLine())!=null){
                    if (line.toLowerCase().startsWith("feature_id=")){
                        String id = line.split("=")[1];
                        if (!id.equals(lastID)){
                            featureIDs.add(id);
                            lastID = id;
                        }
                    }
                }
            }


//            if (isAllIdsOrdered(featureIDs)) System.out.println("all experiment ids in ascending order without any missing");


            final MsExperimentParser parser = new MsExperimentParser();
            List<Ms2Experiment> experiments = parser.getParser(mgfFile.toFile()).parseFromFile(mgfFile.toFile());


            //todo clean string !?!?
            final Map<String, Ms2Experiment> experimentMap = new HashMap<>();
            for (Ms2Experiment experiment : experiments) {
                String name = cleanString(experiment.getName());
                if (experimentMap.containsKey(name)) throw new IOException("compound id duplicate: "+name+". Ids must be unambiguous to map library hits");
                experimentMap.put(name, experiment);
            }

            //todo change nasty hack


            List<String> lines = Files.readAllLines(libraryHitsPath, Charset.defaultCharset());
            String[] header = lines.remove(0).split("\t");
//        String[] ofInterest = new String[]{"Feature_id", "Formula", "Structure", "Adduct", "Cosine", "SharedPeaks", "Quality"};
            String[] ofInterest = new String[]{"#Scan#", "INCHI", "Smiles", "Adduct", "MQScore", "SharedPeaks", "Quality"};
            int[] indices = new int[ofInterest.length];
            for (int i = 0; i < ofInterest.length; i++) {
                int idx = arrayFind(header, ofInterest[i]);
                if (idx<0){
                    int[] more = arrayFindSimilar(header, ofInterest[i]);
                    if (more.length!=1) throw new IOException("Cannot parse spectral library hits file. Column "+ofInterest[i]+" not found.");
                    else idx = more[0];
                }
                indices[i] = idx;
            }


            List<LibraryHit> libraryHits = new ArrayList<>();
            for (String line : lines) {
                try {
                    String[] cols = line.split("\t");
                    final int scanNumber = Integer.parseInt(cols[indices[0]]);
                    final String featureId = featureIDs.get(scanNumber-1); //starting with 1!

                    final Ms2Experiment experiment = experimentMap.get(featureId);
                    final MolecularFormula formula = getFormulaFromStructure(cols[indices[1]].replace("\"", ""), cols[indices[2]].replace("\"", ""));

                    if (formula==null){
                        logger.warn("Cannot parse molecular formula of library hit #SCAN# "+scanNumber);
                        continue;
                    }

                    final String structure = (isInchi(cols[indices[1]]) ? cols[indices[1]] : cols[indices[2]]);
                    final PrecursorIonType ionType = PeriodicTable.getInstance().ionByName(cols[indices[3]]);
                    final double cosine = Double.parseDouble(cols[indices[4]]);
                    final int sharedPeaks = Integer.parseInt(cols[indices[5]]);
                    final LibraryHitQuality quality = LibraryHitQuality.valueOf(cols[indices[6]]);
                    LibraryHit libraryHit = new LibraryHit(experiment, formula, structure, ionType, cosine, sharedPeaks, quality);
                    libraryHits.add(libraryHit);
                } catch (Exception e) {
                    logger.warn("Cannot parse library hit. Reason: "+ e.getMessage());
                }

            }

            return libraryHits;
        } catch (Exception e){
            throw new IOException("cannot parse library hits. Reason "+e.getMessage());
        }

    }

    private static MolecularFormula getFormulaFromStructure(String inchi, String smiles){

        MolecularFormula formula = null;
        if (inchi!=null && isInchi(inchi)){
            formula = new InChI(null, inchi).extractFormula();
        }

        if (formula==null){
            try {
                final SmilesParser parser = new SmilesParser(DefaultChemObjectBuilder.getInstance());
                final IAtomContainer c = parser.parseSmiles(smiles);
                String formulaString = MolecularFormulaManipulator.getString(MolecularFormulaManipulator.getMolecularFormula(c));
                formula = MolecularFormula.parse(formulaString);
            } catch (CDKException e) {
                return null;
            }
        }
        return formula;
    }

    private static boolean isInchi(String inchi) {
        if (!inchi.toLowerCase().startsWith("inchi=")) return false;
        int idx1 = inchi.indexOf("/");
        int idx2 = inchi.indexOf("/", idx1+1);
        if (idx1>0 && idx2>0 && (idx2-idx1)>1) return true;
        return false;
    }

    private static <T> int arrayFind(T[] array, T object) {
        for(int i = 0; i < array.length; ++i) {
            Object t = array[i];
            if(t.equals(object)) {
                return i;
            }
        }

        return -1;
    }

    private static int[] arrayFindSimilar(String[] array, String object) {
        TIntArrayList positions = new TIntArrayList();
        for(int i = 0; i < array.length; ++i) {
            String t = array[i];
            if(t.toLowerCase().contains(object.toLowerCase())) {
                positions.add(i);
            }
        }

        return positions.toArray();
    }

    private static TCharSet forbidden = new TCharHashSet(new char[]{' ',':','\\','/','[',']', '_'});
    private static String cleanString(String s){
        final StringBuilder builder = new StringBuilder(s.length());
        final char[] chars = s.toCharArray();
        for (char c : chars) {
            if (!forbidden.contains(c)) builder.append(c);
        }
        return builder.toString();
    }
}
