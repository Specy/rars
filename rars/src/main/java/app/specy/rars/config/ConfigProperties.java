package app.specy.rars.config;

public class ConfigProperties extends ConfigMap {
    public static final String MessageLimit = "MessageLimit";
    public static final String ErrorLimit = "ErrorLimit";
    public static final String BackstepLimit = "BackstepLimit";
    public static final String Extensions = "Extensions";
    public static final String AsciiTable = "AsciiTable";
    public static final String AsciiNonPrint = "AsciiNonPrint";

    public ConfigProperties() {
        reset();
    }

    public void reset() {
        put(MessageLimit, "1000000");
        put(ErrorLimit, "200");
        put(BackstepLimit, "2000");
        put(Extensions, "asm  s");
        put(AsciiTable, "\0 null null null null null null null  \b  \t\n" +
      "\n  \u000B  \f  \r null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null space   !    \"    #    $    %    &    ' \n" +
      "(    )    *    +    ,    -    .    /    0    1 \n" +
      "2    3    4    5    6    7    8    9    :    ; \n" +
      "<    =    >    ?    @    A    B    C    D    E \n" +
      "F    G    H    I    J    K    L    M    N    O \n" +
      "P    Q    R    S    T    U    V    W    X    Y \n" +
      "Z    [   \\    ]    ^    _    `    a    b    c \n" +
      "d    e    f    g    h    i    j    k    l    m \n" +
      "n    o    p    q    r    s    t    u    v    w \n" +
      "x    y    z    {    |    }    ~ null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null null null null null \n" +
      "null null null null null null");
        put(AsciiNonPrint, ".");
    }
}
