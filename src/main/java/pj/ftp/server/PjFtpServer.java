package pj.ftp.server;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ItemEvent;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JRootPane;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.ftpserver.ConnectionConfig;
import org.apache.ftpserver.ConnectionConfigFactory;
import org.apache.ftpserver.FtpServer;
import org.apache.ftpserver.FtpServerConfigurationException;
import org.apache.ftpserver.FtpServerFactory;
import org.apache.ftpserver.ftplet.Authority;
import org.apache.ftpserver.ftplet.FtpException;
import org.apache.ftpserver.ftplet.UserManager;
import org.apache.ftpserver.ipfilter.IpFilterType;
import org.apache.ftpserver.ipfilter.RemoteIpFilter;
import org.apache.ftpserver.ipfilter.SessionFilter;
import org.apache.ftpserver.listener.ListenerFactory;
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory;
import org.apache.ftpserver.usermanager.impl.BaseUser;
import org.apache.ftpserver.usermanager.impl.ConcurrentLoginPermission;
import org.apache.ftpserver.usermanager.impl.TransferRatePermission;
import org.apache.ftpserver.usermanager.impl.WritePermission;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

public class PjFtpServer extends javax.swing.JFrame {

    public static Boolean running = false;
    public static FtpServer server;
    public static int MAX_CONCURRENT_LOGINS = 11;
    public static int MAX_CONCURRENT_LOGINS_PER_IP = 11;
    public static int MAX_IDLE_TIME = 9999;
    public static int MAX_THREADS_LOGINS = 128;
    public static int MAX_SPEED = 125_000_000;// = Integer.MAX_VALUE;99_999;//Integer.MAX_VALUE; = in Kbit/sek !!
    public static Boolean writeAccess = true;
    //public static MessageResource mrLog;
    //public static java.util.logging.Logger jul;
    public static org.apache.log4j.Logger j4log;
    public static PjFtpServer frame;
    public static Map<String, String> argsHM = new HashMap<String, String>();
    public static Thread Log_Thread;
    public static String allowNetAddress = ICFG.allowNetDefaultAddress;
    public static String allowNetPrefixMask = ICFG.allowNetDefaultPrefixMask;
    public static SessionFilter sessionFilter;
    public static Boolean ipFilterEnabled = false;

    //public static List<String> listListenIP = new ArrayList<>();

    /*static {
        try (FileInputStream ins = new FileInputStream("cfg/jul.properties")) {
            LogManager.getLogManager().readConfiguration(ins);
            jul = java.util.logging.Logger.getLogger(FTPTestServer.class.getName());
        } catch (Exception ignore) { ignore.printStackTrace(); }
    } */
    
    public PjFtpServer() {
        initComponents();
        ImageIcon icone = new ImageIcon(getClass().getResource("/img/top-frame-triangle-16.png"));
        this.setIconImage(icone.getImage());
        this.setTitle(ICFG.zagolovok);
        //
        this.comboSpeed.setModel(new DefaultComboBoxModel<>(ActionsFacade.speedMap.keySet().stream().sorted().toArray(String[]::new)));
        this.comboSpeed.setEditable(false);
        this.comboSpeed.setSelectedItem("125 Mbyte/s=1000 Mbit/s");
        MAX_SPEED=ActionsFacade.speedMap.get(comboSpeed.getSelectedItem().toString());
        System.out.println(maxSpeedString()); 
        //
        this.comboMaxLogins.setModel(new DefaultComboBoxModel<>(ActionsFacade.loginsArray));
        this.comboMaxLogins.setEditable(false);
        this.comboMaxLogins.setSelectedItem("100");
        MAX_THREADS_LOGINS=Integer.parseInt(comboMaxLogins.getSelectedItem().toString());        
        System.out.println("Max Logins = "+MAX_THREADS_LOGINS);
        //
        this.comboMaxLoginsPerIP.setModel(new DefaultComboBoxModel<>(ActionsFacade.loginsArrayPerIP));
        this.comboMaxLoginsPerIP.setEditable(false);
        this.comboMaxLoginsPerIP.setSelectedItem("3");
        MAX_CONCURRENT_LOGINS_PER_IP=Integer.parseInt(comboMaxLoginsPerIP.getSelectedItem().toString());        
        System.out.println("Max Logins Per IP = "+MAX_CONCURRENT_LOGINS_PER_IP);
        // 
        this.comboWritable.setModel(new DefaultComboBoxModel<>(ActionsFacade.writableArray));
        this.comboWritable.setEditable(false);
        writeAccess=Boolean.parseBoolean(comboWritable.getSelectedItem().toString());
        System.out.println("Writable = "+writeAccess); 
        // 
        this.comboPrefixMask.setModel(new DefaultComboBoxModel<>(ActionsFacade.allowNetPrefixMaskArray));
        this.comboPrefixMask.setEditable(false);
        allowNetPrefixMask=comboPrefixMask.getSelectedItem().toString().trim();
        System.out.println("Allow Network = "+allowNetAddress+allowNetPrefixMask.split("=")[0]); 
        //        
        this.comboListenIP.setModel(new DefaultComboBoxModel<>(ActionsFacade.listLocalIpAddr().stream().toArray(String[]::new))); 
        this.comboListenIP.setEditable(false);
        this.taLog.setBackground(Color.BLACK);
        this.taLog.setForeground(Color.CYAN);
        this.tfFolder.setEditable(false);
        this.tfAllowNet.setText(ICFG.allowNetDefaultAddress);
        //this.tfAllowNet.setSize(77, 24);
        this.tfAllowNet.setMaximumSize(ICFG.tfAllowNetSize);
        this.tfAllowNet.setMinimumSize(ICFG.tfAllowNetSize);
        this.tfAllowNet.setPreferredSize(ICFG.tfAllowNetSize);
        tfAllowNet.setEnabled(false);
        comboPrefixMask.setEnabled(false);        
    }
    
    public static String maxSpeedString () {
        return "Max speed = " + String.format("%3.1f", (0.0+MAX_SPEED)/1000000) + " Mbyte/s = " + String.format("%3.1f", 8*(0.0+MAX_SPEED)/1000000) + " Mbit/s";
    }

    private synchronized static void startServer(String args[], String tcpPort, String login, String password, String folder, String listenIP) throws FtpException, FtpServerConfigurationException {
        //File propertiesFile = new File("cfg/log4j.properties");
        //PropertyConfigurator.configure(propertiesFile.toString());
        PropertyConfigurator.configure("cfg/log4j.properties");
        //DOMConfigurator.configure("cfg/log4j.xml"); 
        j4log = Logger.getLogger(PjFtpServer.class.getName());
        //jul.config(msg);

        PropertiesUserManagerFactory userManagerFactory = new PropertiesUserManagerFactory();
        UserManager userManager = userManagerFactory.createUserManager();
        BaseUser user = new BaseUser();
        user.setName(login);
        user.setPassword(password);
        user.setHomeDirectory(folder);
        List<Authority> authorities = new ArrayList<Authority>();
        if (writeAccess) authorities.add(new WritePermission());
        authorities.add(new ConcurrentLoginPermission(MAX_CONCURRENT_LOGINS, MAX_CONCURRENT_LOGINS_PER_IP));
        authorities.add(new TransferRatePermission(MAX_SPEED, MAX_SPEED));
        user.setAuthorities(authorities);
        user.setMaxIdleTime(MAX_IDLE_TIME);
        userManager.save(user);
        
        ListenerFactory listenerFactory = new ListenerFactory();
        listenerFactory.setPort(Integer.parseInt(tcpPort));
        listenerFactory.setServerAddress(listenIP);
        listenerFactory.setIdleTimeout(MAX_IDLE_TIME);
        j4log.log(Level.INFO, "pj-ftp-server try to start");
        j4log.log(Level.INFO, "try to start at = " + ICFG.sdtf.format(new Date()));
        if (args.length == 0 && ipFilterEnabled) {
        try {
            allowNetAddress=tfAllowNet.getText().trim();
            allowNetPrefixMask=comboPrefixMask.getSelectedItem().toString().trim();
            //System.out.println("Allow Network = "+allowNetAddress+allowNetPrefixMask.split("=")[0]);             
            //sessionFilter = new RemoteIpFilter(IpFilterType.ALLOW, allowNetAddress + allowNetPrefixMask.split("=")[0]);
            RemoteIpFilter rif = new RemoteIpFilter(IpFilterType.ALLOW);
            boolean bnet=rif.add(allowNetAddress + allowNetPrefixMask.split("=")[0]);
            boolean bloop=rif.add("127.0.0.1"); //- BLOCK LOOP-BACK IF NOT LISTEN ON THIS !!!!!!!!!!!!!!!!!!!!!
            if (bnet && bloop) {
                System.out.println("Allow Network = " +allowNetAddress + allowNetPrefixMask.split("=")[0] +" - IpFilter make success !");
                j4log.log(Level.INFO, "Allow Network = " +allowNetAddress + allowNetPrefixMask.split("=")[0] +" - IpFilter make success !");
            }
            sessionFilter=rif;
            listenerFactory.setSessionFilter(sessionFilter);
        } catch (NumberFormatException | UnknownHostException ex) {
            java.util.logging.Logger.getLogger(PjFtpServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }            
        }

        FtpServerFactory ftpServerFactory = new FtpServerFactory();
        ftpServerFactory.setUserManager(userManager);
        ftpServerFactory.addListener("default", listenerFactory.createListener());

        ConnectionConfigFactory configFactory = new ConnectionConfigFactory();
        //configFactory.setAnonymousLoginEnabled(true);
        configFactory.setMaxThreads(4 + MAX_THREADS_LOGINS);
        configFactory.setMaxAnonymousLogins(MAX_THREADS_LOGINS);
        configFactory.setMaxLogins(MAX_THREADS_LOGINS);
        ConnectionConfig connectionConfig = configFactory.createConnectionConfig();
        ftpServerFactory.setConnectionConfig(connectionConfig);
        //mrLog = factory.getMessageResource();
        //Map<String, String> hmLog = mrLog.getMessages("INFO");

        server = ftpServerFactory.createServer();
        server.start();
        //jul.log(Level.SEVERE, "oppanki");
        j4log.log(Level.INFO, "pj-ftp-server running");
        j4log.log(Level.INFO, "Max Threads = "+connectionConfig.getMaxThreads());
        if (args.length == 0 && tfUser.getText().trim().equals("anonymous")) {
            j4log.log(Level.INFO, "Anonymous Login Enabled by default = "+connectionConfig.isAnonymousLoginEnabled());
            j4log.log(Level.INFO, "Max Anonymous Logins = "+connectionConfig.getMaxAnonymousLogins());
        }
        j4log.log(Level.INFO, "Max Logins = "+connectionConfig.getMaxLogins());
        j4log.log(Level.INFO, "Max Logins per IP = "+MAX_CONCURRENT_LOGINS_PER_IP);
        j4log.log(Level.INFO, "Server Address = "+listenerFactory.getServerAddress());
        j4log.log(Level.INFO, "Server Port = "+listenerFactory.getPort());
        j4log.log(Level.INFO, "Server Idle TimeOut = "+listenerFactory.getIdleTimeout());
        j4log.log(Level.INFO, "Writable = "+writeAccess);
        j4log.log(Level.INFO, maxSpeedString());
        if (args.length == 0 && ipFilterEnabled) j4log.log(Level.INFO, "Allow Network = "+allowNetAddress+allowNetPrefixMask.split("=")[0]);
        running = true;
        if (args.length == 0) {
            Log_Thread = new Log_Thread("log/app.log");
            try {
                Log_Thread.start();
            } catch (IllegalThreadStateException itse) {}
            frame.setTitle(ICFG.zagolovok + ", server running");
        }
    }

    /*public void changeLF() {
        String changeLook = (String) JOptionPane.showInputDialog(frame, "Choose Look and Feel Here:", "Select Look and Feel", JOptionPane.QUESTION_MESSAGE, new ImageIcon(getClass().getResource("/img/color_swatch.png")), lookAndFeelsDisplay.toArray(), null);
        if (changeLook != null) {
            for (int a = 0; a < lookAndFeelsDisplay.size(); a++) {
                if (changeLook.equals(lookAndFeelsDisplay.get(a))) {
                    currentLAF = lookAndFeelsRealNames.get(a);
                    setLF(frame);
                    break;
                }
            }
        }
    }*/

    private void setBooleanBtnTf(Boolean sset) {
        tfUser.setEditable(sset);
        tfPassw.setEditable(sset);
        tfPort.setEditable(sset);
        tfAllowNet.setEnabled(sset);
        //
        comboPrefixMask.setEnabled(sset);
        comboListenIP.setEnabled(sset);
        comboSpeed.setEnabled(sset);
        comboMaxLogins.setEnabled(sset);
        comboMaxLoginsPerIP.setEnabled(sset);
        comboWritable.setEnabled(sset);
        //
        checkBoxAnonymous.setEnabled(sset);
        checkBoxIpFilter.setEnabled(sset);
        btnSelectFolder.setEnabled(sset);
        if (checkBoxAnonymous.isSelected()) {
            tfUser.setEditable(false);
            tfPassw.setEditable(false);
        }
        if (!checkBoxIpFilter.isSelected()) {
            tfAllowNet.setEnabled(false);
            comboPrefixMask.setEnabled(false);
        }        
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jToolBar1 = new javax.swing.JToolBar();
        jSeparator14 = new javax.swing.JToolBar.Separator();
        jLabel4 = new javax.swing.JLabel();
        comboListenIP = new javax.swing.JComboBox<>();
        jSeparator12 = new javax.swing.JToolBar.Separator();
        jLabel1 = new javax.swing.JLabel();
        tfPort = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JToolBar.Separator();
        jLabel2 = new javax.swing.JLabel();
        tfUser = new javax.swing.JTextField();
        jSeparator2 = new javax.swing.JToolBar.Separator();
        jLabel3 = new javax.swing.JLabel();
        tfPassw = new javax.swing.JTextField();
        jSeparator4 = new javax.swing.JToolBar.Separator();
        checkBoxAnonymous = new javax.swing.JCheckBox();
        checkBoxIpFilter = new javax.swing.JCheckBox();
        jSeparator11 = new javax.swing.JToolBar.Separator();
        btnToggleRunStop = new javax.swing.JToggleButton();
        jSeparator7 = new javax.swing.JToolBar.Separator();
        jToolBar3 = new javax.swing.JToolBar();
        jSeparator9 = new javax.swing.JToolBar.Separator();
        jLabel5 = new javax.swing.JLabel();
        comboSpeed = new javax.swing.JComboBox<>();
        jSeparator15 = new javax.swing.JToolBar.Separator();
        jLabel6 = new javax.swing.JLabel();
        comboMaxLogins = new javax.swing.JComboBox<>();
        jSeparator16 = new javax.swing.JToolBar.Separator();
        jLabel7 = new javax.swing.JLabel();
        comboMaxLoginsPerIP = new javax.swing.JComboBox<>();
        jSeparator17 = new javax.swing.JToolBar.Separator();
        jLabel8 = new javax.swing.JLabel();
        comboWritable = new javax.swing.JComboBox<>();
        jSeparator18 = new javax.swing.JToolBar.Separator();
        jLabel9 = new javax.swing.JLabel();
        tfAllowNet = new javax.swing.JTextField();
        comboPrefixMask = new javax.swing.JComboBox<>();
        jPanel2 = new javax.swing.JPanel();
        jScrollPane2 = new javax.swing.JScrollPane();
        taLog = new javax.swing.JTextArea();
        jPanel3 = new javax.swing.JPanel();
        jToolBar2 = new javax.swing.JToolBar();
        jSeparator3 = new javax.swing.JToolBar.Separator();
        btnSelectFolder = new javax.swing.JButton();
        jSeparator6 = new javax.swing.JToolBar.Separator();
        tfFolder = new javax.swing.JTextField();
        jSeparator10 = new javax.swing.JToolBar.Separator();
        btnClearLog = new javax.swing.JButton();
        jSeparator8 = new javax.swing.JToolBar.Separator();
        btnAbout = new javax.swing.JButton();
        jSeparator13 = new javax.swing.JToolBar.Separator();
        btnQuit = new javax.swing.JButton();
        jSeparator5 = new javax.swing.JToolBar.Separator();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("pj-ftp-server");
        setLocation(new java.awt.Point(99, 99));
        setMinimumSize(new java.awt.Dimension(800, 500));
        setUndecorated(true);
        setPreferredSize(new java.awt.Dimension(800, 500));
        setSize(new java.awt.Dimension(800, 500));

        jPanel1.setBorder(javax.swing.BorderFactory.createTitledBorder("parameters and actions"));
        jPanel1.setLayout(new java.awt.BorderLayout());

        jToolBar1.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuration:"));
        jToolBar1.setFloatable(false);
        jToolBar1.add(jSeparator14);

        jLabel4.setText("Listen IP:");
        jToolBar1.add(jLabel4);

        comboListenIP.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "127.0.0.1" }));
        jToolBar1.add(comboListenIP);
        jToolBar1.add(jSeparator12);

        jLabel1.setText("Port:");
        jToolBar1.add(jLabel1);

        tfPort.setText("21");
        jToolBar1.add(tfPort);
        jToolBar1.add(jSeparator1);

        jLabel2.setText("User:");
        jToolBar1.add(jLabel2);
        jToolBar1.add(tfUser);
        jToolBar1.add(jSeparator2);

        jLabel3.setText("Password:");
        jToolBar1.add(jLabel3);
        jToolBar1.add(tfPassw);
        jToolBar1.add(jSeparator4);

        checkBoxAnonymous.setText("Anonymous mode");
        checkBoxAnonymous.setFocusable(false);
        checkBoxAnonymous.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        checkBoxAnonymous.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        checkBoxAnonymous.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                checkBoxAnonymousItemStateChanged(evt);
            }
        });
        jToolBar1.add(checkBoxAnonymous);

        checkBoxIpFilter.setText("Enable IP-Filter");
        checkBoxIpFilter.setFocusable(false);
        checkBoxIpFilter.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        checkBoxIpFilter.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        checkBoxIpFilter.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                checkBoxIpFilterItemStateChanged(evt);
            }
        });
        jToolBar1.add(checkBoxIpFilter);
        jToolBar1.add(jSeparator11);

        btnToggleRunStop.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/go-green-krug-16.png"))); // NOI18N
        btnToggleRunStop.setText("Run server ");
        btnToggleRunStop.setFocusable(false);
        btnToggleRunStop.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnToggleRunStop.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                btnToggleRunStopItemStateChanged(evt);
            }
        });
        jToolBar1.add(btnToggleRunStop);
        jToolBar1.add(jSeparator7);

        jPanel1.add(jToolBar1, java.awt.BorderLayout.CENTER);

        jToolBar3.setBorder(javax.swing.BorderFactory.createTitledBorder("Configuration:"));
        jToolBar3.setFloatable(false);
        jToolBar3.add(jSeparator9);

        jLabel5.setText("MAX speed:");
        jToolBar3.add(jLabel5);

        comboSpeed.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 111" }));
        comboSpeed.setToolTipText("");
        comboSpeed.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboSpeedActionPerformed(evt);
            }
        });
        jToolBar3.add(comboSpeed);
        jToolBar3.add(jSeparator15);

        jLabel6.setText("MAX logins:");
        jToolBar3.add(jLabel6);

        comboMaxLogins.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 111" }));
        comboMaxLogins.setToolTipText("");
        comboMaxLogins.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboMaxLoginsActionPerformed(evt);
            }
        });
        jToolBar3.add(comboMaxLogins);
        jToolBar3.add(jSeparator16);

        jLabel7.setText("Max login per IP:");
        jToolBar3.add(jLabel7);

        comboMaxLoginsPerIP.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 111" }));
        comboMaxLoginsPerIP.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboMaxLoginsPerIPActionPerformed(evt);
            }
        });
        jToolBar3.add(comboMaxLoginsPerIP);
        jToolBar3.add(jSeparator17);

        jLabel8.setText("Writable:");
        jToolBar3.add(jLabel8);

        comboWritable.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 111" }));
        comboWritable.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboWritableActionPerformed(evt);
            }
        });
        jToolBar3.add(comboWritable);
        jToolBar3.add(jSeparator18);

        jLabel9.setText("Allow Network:");
        jToolBar3.add(jLabel9);

        tfAllowNet.setHorizontalAlignment(javax.swing.JTextField.LEFT);
        tfAllowNet.setText("10.0.0.0");
        jToolBar3.add(tfAllowNet);

        comboPrefixMask.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 111" }));
        comboPrefixMask.setToolTipText("");
        comboPrefixMask.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                comboPrefixMaskActionPerformed(evt);
            }
        });
        jToolBar3.add(comboPrefixMask);

        jPanel1.add(jToolBar3, java.awt.BorderLayout.PAGE_START);

        getContentPane().add(jPanel1, java.awt.BorderLayout.PAGE_START);

        jPanel2.setBorder(javax.swing.BorderFactory.createTitledBorder("Log-File content - /log/app.log"));
        jPanel2.setLayout(new java.awt.BorderLayout());

        taLog.setColumns(20);
        taLog.setRows(5);
        jScrollPane2.setViewportView(taLog);

        jPanel2.add(jScrollPane2, java.awt.BorderLayout.CENTER);

        getContentPane().add(jPanel2, java.awt.BorderLayout.CENTER);

        jPanel3.setBorder(javax.swing.BorderFactory.createTitledBorder("parameters and actions"));
        jPanel3.setLayout(new java.awt.BorderLayout());

        jToolBar2.setFloatable(false);
        jToolBar2.add(jSeparator3);

        btnSelectFolder.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/folder-green-16.png"))); // NOI18N
        btnSelectFolder.setText("Select Folder: ");
        btnSelectFolder.setFocusable(false);
        btnSelectFolder.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnSelectFolder.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnSelectFolder.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSelectFolderActionPerformed(evt);
            }
        });
        jToolBar2.add(btnSelectFolder);
        jToolBar2.add(jSeparator6);

        tfFolder.setEditable(false);
        tfFolder.setText("/tmp");
        jToolBar2.add(tfFolder);
        jToolBar2.add(jSeparator10);

        btnClearLog.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/clear-yellow-16.png"))); // NOI18N
        btnClearLog.setText("Clear Log");
        btnClearLog.setFocusable(false);
        btnClearLog.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnClearLog.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnClearLogActionPerformed(evt);
            }
        });
        jToolBar2.add(btnClearLog);
        jToolBar2.add(jSeparator8);

        btnAbout.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/info-cyan-16.png"))); // NOI18N
        btnAbout.setText(" About");
        btnAbout.setFocusable(false);
        btnAbout.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnAbout.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnAbout.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAboutActionPerformed(evt);
            }
        });
        jToolBar2.add(btnAbout);
        jToolBar2.add(jSeparator13);

        btnQuit.setIcon(new javax.swing.ImageIcon(getClass().getResource("/img/quit-16.png"))); // NOI18N
        btnQuit.setText("Quit");
        btnQuit.setFocusable(false);
        btnQuit.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
        btnQuit.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        btnQuit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnQuitActionPerformed(evt);
            }
        });
        jToolBar2.add(btnQuit);
        jToolBar2.add(jSeparator5);

        jPanel3.add(jToolBar2, java.awt.BorderLayout.CENTER);

        getContentPane().add(jPanel3, java.awt.BorderLayout.PAGE_END);

        getAccessibleContext().setAccessibleDescription("");

        pack();
        setLocationRelativeTo(null);
    }// </editor-fold>//GEN-END:initComponents

    private void checkBoxAnonymousItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_checkBoxAnonymousItemStateChanged
        if (tfUser.getText().trim().equals("anonymous")) {
            tfUser.setText("");
            tfPassw.setText("");
            tfUser.setEditable(true);
            tfPassw.setEditable(true);
        } else {
            tfUser.setText("anonymous");
            tfPassw.setText("jer@sey.com");
            tfUser.setEditable(false);
            tfPassw.setEditable(false);
        }
    }//GEN-LAST:event_checkBoxAnonymousItemStateChanged

    private void btnQuitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnQuitActionPerformed
        int r = JOptionPane.showConfirmDialog(frame, "Really Quit ?", "Quit ?", JOptionPane.YES_NO_OPTION);
        if (r == JOptionPane.YES_OPTION) {
            try {
                server.stop();
            } catch (NullPointerException ne) {        }
            System.exit(0);           
        }
    }//GEN-LAST:event_btnQuitActionPerformed

    private void btnToggleRunStopItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_btnToggleRunStopItemStateChanged
        if (!ActionsFacade.checkTcpPort(tfPort.getText().trim())) {
            JOptionPane.showMessageDialog(frame, "Port wrong !", "Error", JOptionPane.ERROR_MESSAGE); 
            btnToggleRunStop.setSelected(false);
            return;
        }
        if (tfUser.getText().isEmpty() || tfPassw.getText().isEmpty() || tfFolder.getText().isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Some wrong parameters !", "Error", JOptionPane.ERROR_MESSAGE);
            btnToggleRunStop.setSelected(false);
            return;
        }
        if (!ICFG.ipv.isValid(comboListenIP.getSelectedItem().toString().trim()))  {
            JOptionPane.showMessageDialog(frame, "Wrong listen IP-address !", "Error", JOptionPane.ERROR_MESSAGE);
            btnToggleRunStop.setSelected(false);
            return;            
        }
        try { new SubnetUtils(tfAllowNet.getText().trim()+comboPrefixMask.getSelectedItem().toString().trim().split("=")[0]);} 
        catch (IllegalArgumentException iae) {
            JOptionPane.showMessageDialog(frame, "Wrong Network IP-address ! = "+tfAllowNet.getText().trim()+comboPrefixMask.getSelectedItem().toString().trim().split("=")[0], "Error", JOptionPane.ERROR_MESSAGE);
            allowNetAddress = ICFG.allowNetDefaultAddress;
            allowNetPrefixMask = ICFG.allowNetDefaultPrefixMask;
            comboPrefixMask.setSelectedItem(allowNetPrefixMask);
            tfAllowNet.setText(allowNetAddress);
            return;
        }  
        ImageIcon iconOn = new ImageIcon(getClass().getResource("/img/go-green-krug-16.png"));
        ImageIcon iconOf = new ImageIcon(getClass().getResource("/img/stop-16.png"));
        if (evt.getStateChange() == ItemEvent.DESELECTED) {
            if (running == true) {
                server.stop();
                btnToggleRunStop.setIcon(iconOn);
                btnToggleRunStop.setText("Run server");
                setBooleanBtnTf(true);
                taLog.grabFocus();//.setFocusable(true);
                frame.setTitle(ICFG.zagolovok + ", server stop");
                j4log.log(Level.INFO, "pj-ftp-server stop");
                return;
            }
        }
        if (evt.getStateChange() == ItemEvent.SELECTED) {
            try {
                startServer(new String[0], tfPort.getText().trim(), tfUser.getText().trim(), tfPassw.getText().trim(), tfFolder.getText().trim(), comboListenIP.getSelectedItem().toString().trim());
                btnToggleRunStop.setIcon(iconOf);
                btnToggleRunStop.setText("Stop server");
                setBooleanBtnTf(false);
            } catch (FtpException | FtpServerConfigurationException fe) {
                JOptionPane.showMessageDialog(frame, "Some wrong !", "Error", JOptionPane.ERROR_MESSAGE);
                btnToggleRunStop.setSelected(false);
            }
        }

    }//GEN-LAST:event_btnToggleRunStopItemStateChanged

    private void btnSelectFolderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSelectFolderActionPerformed
        JFileChooser myd = new JFileChooser();
        //myd.addChoosableFileFilter(new AudioFileFilter());
        //myd.setAcceptAllFileFilterUsed(false);
        myd.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        switch (myd.showDialog(frame, "Select Folder")) {
            case JFileChooser.APPROVE_OPTION:
                //ftpFolder = myd.getSelectedFile().getPath();
                tfFolder.setText(myd.getSelectedFile().getPath());
                //putd = myd.getSelectedFile() + "";
                break;
            case JFileChooser.CANCEL_OPTION:
                break;
        }//switch
    }//GEN-LAST:event_btnSelectFolderActionPerformed

    private void btnAboutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAboutActionPerformed
        ActionsFacade.about(new ImageIcon(getClass().getResource("/img/logo/ftp-green-logo-128.png")));
    }//GEN-LAST:event_btnAboutActionPerformed

    private void btnClearLogActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnClearLogActionPerformed
        try {
            new PrintWriter("log/app.log").close();
            taLog.setText("");
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(PjFtpServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }        
        /*try (PrintWriter writer = new PrintWriter("log/app.log")) {
            writer.print("");
            writer.close();
            taLog.setText("");
            //taLog.repaint();
            //taLog.updateUI();
        } catch (FileNotFoundException ex) {
            java.util.logging.Logger.getLogger(PjFtpServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }*/
    }//GEN-LAST:event_btnClearLogActionPerformed

    private void comboSpeedActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboSpeedActionPerformed
        MAX_SPEED=ActionsFacade.speedMap.get(comboSpeed.getSelectedItem().toString());
        System.out.println(maxSpeedString()); 
    }//GEN-LAST:event_comboSpeedActionPerformed

    private void comboMaxLoginsActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboMaxLoginsActionPerformed
        MAX_THREADS_LOGINS=Integer.parseInt(comboMaxLogins.getSelectedItem().toString());        
        System.out.println("Max Logins = "+MAX_THREADS_LOGINS);
    }//GEN-LAST:event_comboMaxLoginsActionPerformed

    private void comboMaxLoginsPerIPActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboMaxLoginsPerIPActionPerformed
        MAX_CONCURRENT_LOGINS_PER_IP=Integer.parseInt(comboMaxLoginsPerIP.getSelectedItem().toString());        
        System.out.println("Max Logins Per IP = "+MAX_CONCURRENT_LOGINS_PER_IP);
    }//GEN-LAST:event_comboMaxLoginsPerIPActionPerformed

    private void comboWritableActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboWritableActionPerformed
        writeAccess=Boolean.parseBoolean(comboWritable.getSelectedItem().toString());
        System.out.println("Writable = "+writeAccess);
    }//GEN-LAST:event_comboWritableActionPerformed

    private void comboPrefixMaskActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_comboPrefixMaskActionPerformed
        allowNetPrefixMask=comboPrefixMask.getSelectedItem().toString().trim();
        System.out.println("Allow Network = "+allowNetAddress+allowNetPrefixMask.split("=")[0]); 
    }//GEN-LAST:event_comboPrefixMaskActionPerformed

    private void checkBoxIpFilterItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_checkBoxIpFilterItemStateChanged
        if (ipFilterEnabled) {
            ipFilterEnabled=false;
            tfAllowNet.setEnabled(false);
            comboPrefixMask.setEnabled(false);            
        } else {
            ipFilterEnabled=true;
            tfAllowNet.setEnabled(true);
            comboPrefixMask.setEnabled(true);            
        }
    }//GEN-LAST:event_checkBoxIpFilterItemStateChanged

    public static void main(String args[]) {
        /*try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Metal".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(PjFtpServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } */
        if (args.length == 0) {
            java.awt.EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {                    
                    frame = new PjFtpServer();
                    ActionsFacade.InstallLF();
                    ActionsFacade.setLF(frame);
                    frame.getRootPane().setWindowDecorationStyle(JRootPane.FRAME);
                    JFrame.setDefaultLookAndFeelDecorated(true);
                    JDialog.setDefaultLookAndFeelDecorated(true);
                    JOptionPane.setRootFrame(frame);
                    frame.setSize(ICFG.FW, ICFG.FH);
                    frame.setLocation(80, 80);
                    frame.setResizable(true);
                    frame.setVisible(true);
                }
            });
        }
        if (args.length > 0) {
            try {
                Arrays.stream(args)
                .forEach(x -> { argsHM.put(x.split("=")[0].toString(), x.split("=")[1].toString()); });
                System.out.println(argsHM);
                String pwd="";
                if (argsHM.get("user").toLowerCase().trim().equals("anonymous")) {
                    pwd="jer@sey.com";
                    argsHM.put("passw", pwd);
                }
                System.out.println(argsHM); 
                if (!ICFG.ipv.isValid(argsHM.get("listenip").trim()))  {
                    System.out.println("Wrong listen IP ! \nExit !"); 
                    ActionsFacade.useExamples();
                    return;
                }
                if (!ActionsFacade.checkTcpPort(argsHM.get("port").trim())) {
                    System.out.println("Port Wrong ! \nExit !"); 
                    ActionsFacade.useExamples();
                    return;
                }                
                try {
                    startServer(args, argsHM.get("port").trim(), argsHM.get("user").trim(), argsHM.get("passw").trim(), argsHM.get("folder").trim(), argsHM.get("listenip").trim());
                } catch (FtpException | FtpServerConfigurationException ex) {
                    java.util.logging.Logger.getLogger(PjFtpServer.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
                    System.out.println("\nNOT run !\nSome of parameters wrong !");
                    ActionsFacade.useExamples();                    
                }
            } catch (NullPointerException | ArrayIndexOutOfBoundsException ne) {
                System.out.println("NOT run !\nSome of parameters not given !");
                System.out.println("Exception = " + ne.toString());
                ActionsFacade.useExamples();
            }
        }
            //}
        //});
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    public static javax.swing.JButton btnAbout;
    public static javax.swing.JButton btnClearLog;
    private javax.swing.JButton btnQuit;
    public static javax.swing.JButton btnSelectFolder;
    public static javax.swing.JToggleButton btnToggleRunStop;
    public static javax.swing.JCheckBox checkBoxAnonymous;
    public static javax.swing.JCheckBox checkBoxIpFilter;
    public static javax.swing.JComboBox<String> comboListenIP;
    public static javax.swing.JComboBox<String> comboMaxLogins;
    public static javax.swing.JComboBox<String> comboMaxLoginsPerIP;
    public static javax.swing.JComboBox<String> comboPrefixMask;
    public static javax.swing.JComboBox<String> comboSpeed;
    public static javax.swing.JComboBox<String> comboWritable;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JToolBar.Separator jSeparator1;
    private javax.swing.JToolBar.Separator jSeparator10;
    private javax.swing.JToolBar.Separator jSeparator11;
    private javax.swing.JToolBar.Separator jSeparator12;
    private javax.swing.JToolBar.Separator jSeparator13;
    private javax.swing.JToolBar.Separator jSeparator14;
    private javax.swing.JToolBar.Separator jSeparator15;
    private javax.swing.JToolBar.Separator jSeparator16;
    private javax.swing.JToolBar.Separator jSeparator17;
    private javax.swing.JToolBar.Separator jSeparator18;
    private javax.swing.JToolBar.Separator jSeparator2;
    private javax.swing.JToolBar.Separator jSeparator3;
    private javax.swing.JToolBar.Separator jSeparator4;
    private javax.swing.JToolBar.Separator jSeparator5;
    private javax.swing.JToolBar.Separator jSeparator6;
    private javax.swing.JToolBar.Separator jSeparator7;
    private javax.swing.JToolBar.Separator jSeparator8;
    private javax.swing.JToolBar.Separator jSeparator9;
    private javax.swing.JToolBar jToolBar1;
    private javax.swing.JToolBar jToolBar2;
    private javax.swing.JToolBar jToolBar3;
    public static javax.swing.JTextArea taLog;
    public static javax.swing.JTextField tfAllowNet;
    public static javax.swing.JTextField tfFolder;
    public static javax.swing.JTextField tfPassw;
    public static javax.swing.JTextField tfPort;
    public static javax.swing.JTextField tfUser;
    // End of variables declaration//GEN-END:variables
}
