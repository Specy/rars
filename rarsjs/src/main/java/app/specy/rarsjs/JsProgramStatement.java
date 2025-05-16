package app.specy.rarsjs;

import app.specy.rars.ProgramStatement;
import org.teavm.jso.JSClass;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSProperty;

@JSClass
public class JsProgramStatement {

    private int sourceLine;
    private int address;
    private int binaryStatement;
    private String source;
    private String assemblyStatement;
    private String machineStatement;


    public JsProgramStatement(ProgramStatement programStatement) {
        this.sourceLine = programStatement.getSourceLine();
        this.address = programStatement.getAddress();
        this.binaryStatement = programStatement.getBinaryStatement();
        this.source = programStatement.getSource();
        this.assemblyStatement = programStatement.getPrintableBasicAssemblyStatement();
        this.machineStatement = programStatement.getMachineStatement();
    }


    @JSExport
    @JSProperty
    public int getSourceLine() {
        return sourceLine;
    }

    @JSExport
    @JSProperty
    public int getAddress() {
        return address;
    }

    @JSExport
    @JSProperty
    public int getBinaryStatement() {
        return binaryStatement;
    }

    @JSExport
    @JSProperty
    public String getSource() {
        return source;
    }

    @JSExport
    @JSProperty
    public String getMachineStatement() {
        return machineStatement;
    }

    @JSExport
    @JSProperty
    public String getAssemblyStatement() {
        return assemblyStatement;
    }

}
