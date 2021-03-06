/*
 *  This file is part of the SIRIUS library for analyzing MS and MS/MS data
 *
 *  Copyright (C) 2013-2015 Kai Dührkop
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with SIRIUS.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.unijena.bioinf.babelms.ms;

import de.unijena.bioinf.ChemistryBase.chem.InChI;
import de.unijena.bioinf.ChemistryBase.chem.Smiles;
import de.unijena.bioinf.ChemistryBase.ms.*;
import de.unijena.bioinf.babelms.DataWriter;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class JenaMsWriter implements DataWriter<Ms2Experiment> {
    @Override
    public void write(BufferedWriter writer, Ms2Experiment data) throws IOException{
        writer.write(">compound ");
        writer.write(data.getName() == null ? "unknown" : data.getName()); writer.newLine();
        writeIfAvailable(writer, ">formula", data.getMolecularFormula());
        writeIf(writer, ">parentmass", String.valueOf(data.getIonMass()), data.getIonMass()!=0d);
        writeIfAvailable(writer, ">ionization", data.getPrecursorIonType().toString());
        final InChI i = data.getAnnotation(InChI.class);
        if (i!=null) {
            writeIfAvailable(writer, ">InChI", i.in2D);
            writeIfAvailable(writer, ">InChIKey", i.key);
        }
        final Smiles sm = data.getAnnotation(Smiles.class);
        writeIfAvailable(writer, ">smarts", sm==null ? null : sm.smiles);
        final Splash splash = data.getAnnotation(Splash.class);
        writeIfAvailable(writer, ">splash", splash==null ? null : splash.getSplash());
        final MsInstrumentation instrumentation = data.getAnnotation(MsInstrumentation.class, MsInstrumentation.Unknown);
        writer.write(">instrumentation " + instrumentation.description());
        writer.newLine();
        writeIfAvailable(writer, ">quality", data.getAnnotation(CompoundQuality.class));
        final Map<String,String> arbitraryKeys = data.getAnnotation(Map.class, new HashMap<String,String>());
        for (Map.Entry<String,String> e : arbitraryKeys.entrySet()) {
            writer.write("#" + e.getKey() + " " + e.getValue());
            writer.newLine();
        }
        writer.newLine();
        for (Spectrum spec : data.getMs1Spectra()) {
            writeMs1(writer, spec);
        }
        for (Ms2Spectrum spec : data.getMs2Spectra()) {
            writeMs2(writer, spec);
        }
    }

    private void writeMs1(BufferedWriter writer, Spectrum spec) throws IOException{
        if (spec != null && spec.size()>0) {
            writer.write(">ms1peaks");
            writer.newLine();
            for (int k=0; k < spec.size(); ++k) {
                writer.write(String.valueOf(spec.getMzAt(k)));
                writer.write(" ");
                writer.write(String.valueOf(spec.getIntensityAt(k)));
                writer.newLine();
            }
            writer.newLine();
        }
    }
    private void writeMs2(BufferedWriter writer, Ms2Spectrum spec) throws IOException{
        if (spec != null && spec.size()>0) {
            if (spec.getCollisionEnergy()==null || spec.getCollisionEnergy().equals(CollisionEnergy.none())) {
                writer.write(">ms2peaks");
            } else {
                writer.write(">collision ");
                writer.write(spec.getCollisionEnergy().toString());
            }
            writer.newLine();
            for (int k=0; k < spec.size(); ++k) {
                writer.write(String.valueOf(spec.getMzAt(k)));
                writer.write(" ");
                writer.write(String.valueOf(spec.getIntensityAt(k)));
                writer.newLine();
            }
            writer.newLine();
        }
    }

    private void writeIf(BufferedWriter writer, String s, String txt, boolean condition) throws IOException {
        if (condition) {
            writer.write(s);
            writer.write(' ');
            writer.write(txt);
            writer.newLine();
        }
    }

    private void writeIfAvailable(BufferedWriter writer, String s, Object o) throws IOException{
        if (o != null) {
            writer.write(s);
            writer.write(' ');
            writer.write(o.toString());
            writer.newLine();
        }
    }
}
