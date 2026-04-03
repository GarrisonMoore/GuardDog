[nxlog_Universal.txt](https://github.com/user-attachments/files/26473689/nxlog_Universal.txt)
# Syslog - Universal Classmate Configuration
## Destination: 10.202.69.23 | Port: 5141 | Protocol: TCP

# ! Instructions !
#### 1. Download the universal config and place it on your desktop - [nxlog_Universal.txt](https://github.com/user-attachments/files/26473689/nxlog_Universal.txt)
#### 2. Install NXlog Community Edition on your server using default file paths
#### 3. Run Notepad as Administrator, select - file > open, and Navigate to C:\Program Files\nxlog\conf\
#### 4. Change file extensions to "All files", and open the "nxlog.conf" file. 
<img width="600" height="400" alt="Screenshot 2026-04-03 162308" src="https://github.com/user-attachments/assets/b77c3884-b189-4dfd-9e68-57fd92059b55" />

#### 5. Open the nxlog_universal.txt file on your dektop, copy everything, and paste it into the actual nxlog.conf file.
#### 6. Open powershell as admin and enter "Restart-Service nxlog"
#### 7. Search your hostname on the Syslog Webpage to mkae sure your logs are being forwarded. http://guarddog.twodoglab.local/

## Raw Config Below

define ROOT C:\Program Files\nxlog
Moduledir %ROOT%\modules
CacheDir %ROOT%\data
Pidfile %ROOT%\data\nxlog.pid
SpoolDir %ROOT%\data
LogFile %ROOT%\data\nxlog.log

<Extension _syslog>
    Module      xm_syslog
</Extension>

<Input in_windows_events>
    Module      im_msvistalog
    Query       <QueryList>\
                    <Query Id="0">\
                        <Select Path="Application">*</Select>\
                        <Select Path="System">*</Select>\
                        <Select Path="Security">*</Select>\
                    </Query>\
                </QueryList>
</Input>

<Output out_syslog_server>
    Module      om_tcp
    Host        10.202.69.23
    Port        5141
    Exec        to_syslog_ietf();
</Output>

<Route 1>
    Path        in_windows_events => out_syslog_server
</Route>
