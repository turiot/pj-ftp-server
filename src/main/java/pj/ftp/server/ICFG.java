
package pj.ftp.server;

import java.awt.Dimension;
import org.apache.commons.validator.routines.InetAddressValidator;

public interface ICFG {
    
    public static int FW = 900;
    public static int FH = 400;
    public static InetAddressValidator ipv = InetAddressValidator.getInstance();
    public static String allowNetDefault = "10.10.0.0/16";
    public static Dimension tfAllowNetSize = new Dimension(144,24);
    public static String currentLAF = "de.muntjak.tinylookandfeel.TinyLookAndFeel";
    public static String zagolovok = " Pure Java FTP Server, v1.0.44, build 20-08-21";    
    
}
