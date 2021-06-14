/* 
 * Copyright (C) 2015-2017 The Language Archive
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package nl.mpi.tla.flat.deposit.action;

import static com.yourmediashelf.fedora.client.FedoraClient.*;
import com.yourmediashelf.fedora.client.response.IngestResponse;
import com.yourmediashelf.fedora.client.response.GetDatastreamResponse;
import com.yourmediashelf.fedora.client.response.ModifyDatastreamResponse;
import java.io.File;
import java.io.FilenameFilter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Date;
import java.util.List;
import nl.mpi.tla.flat.deposit.Context;
import nl.mpi.tla.flat.deposit.DepositException;
import nl.mpi.tla.flat.deposit.sip.Collection;
import nl.mpi.tla.flat.deposit.sip.Resource;
import nl.mpi.tla.flat.deposit.sip.SIPInterface;
import org.apache.commons.io.filefilter.RegexFileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author menzowi
 */
public class FedoraInteract extends FedoraAction {

    private static final Logger logger = LoggerFactory.getLogger(FedoraInteract.class.getName());

    @Override
    public boolean perform(Context context) throws DepositException {
        try {
            connect(context);

            SIPInterface sip = context.getSIP(); 

            File dir = new File(this.getParameter("dir", "./fox"));
            String pre = this.getParameter("namespace", "lat");
            
            // <fid>.xml (FOXML -> ingest)
            File[] foxs = dir.listFiles(((FilenameFilter)new RegexFileFilter(pre+"[A-Za-z0-9_]+\\.xml")));
            for (File fox:foxs) {
                String fid = fox.getName().replace(".xml","").replace(pre+"_",pre+":").replace("_CMD","");
                String dsid = (fox.getName().endsWith("_CMD.xml")?"CMD":"OBJ");
                logger.debug("FOXML["+fox+"] -> ["+fid+"]");
                IngestResponse iResponse = ingest().format("info:fedora/fedora-system:FOXML-1.1").content(fox).logMessage("Initial ingest").ignoreMime(true).execute();
                GetDatastreamResponse dsResponse = getDatastream(fid,dsid).execute();
                if (dsResponse.getStatus()!=200)
                    throw new DepositException("Unexpected status["+dsResponse.getStatus()+"] while interacting with Fedora Commons!");
                logger.info("Created FedoraObject["+iResponse.getPid()+"]["+iResponse.getLocation()+"]["+dsid+"]["+dsResponse.getLastModifiedDate()+"]");
                completeFID(sip,new URI(fid),dsid,dsResponse.getLastModifiedDate());
            }

            // - <fid>.prop (props -> modify (some) properties)
            // TODO
            
            // - <fid>.<dsid>.<asof>.file ... (DS -> modifyDatastream.dsLocation)
            // - <fid>.<dsid>.<asof>.<ext>... (DS -> modifyDatastream.content)
            foxs = dir.listFiles(((FilenameFilter)new RegexFileFilter(pre+"[A-Za-z0-9_]+\\.[A-Z\\-]+\\.[0-9]+\\.[A-Za-z0-9_]+")));
            for (File fox:foxs) {
                String fid  = fox.getName().replaceFirst("\\..*$","").replace(pre+"_",pre+":").replace("_CMD","");
                String dsid = fox.getName().replaceFirst("^.*\\.([A-Z\\-]+)\\..*$","$1");
                String epoch = fox.getName().replaceFirst("^.*\\.([0-9]+)\\..*$","$1");
                Date asof = new Date(Long.parseLong(epoch));
                String ext  = fox.getName().replaceFirst("^.*\\.(.*)$","$1");
                logger.debug("DSID["+fox+"] -> ["+fid+"]["+dsid+"]["+epoch+"="+asof+"]["+ext+"]");
                ModifyDatastreamResponse mdsResponse = null;
                if (ext.equals("file")) {
                    List<String> lines = Files.readAllLines(fox.toPath(),StandardCharsets.UTF_8);
                    if (lines.size()!=1)
                        throw new DepositException("Datastream location file["+fox+"] should contain exactly one line!");
                    mdsResponse = modifyDatastream(fid,dsid).lastModifiedDate(asof).dsLocation(lines.get(0)).logMessage("Updated "+dsid).execute();
                } else {
                    mdsResponse = modifyDatastream(fid,dsid).lastModifiedDate(asof).content(fox).logMessage("Updated "+dsid).execute();
                }
                if (mdsResponse.getStatus()!=200)
                    throw new DepositException("Unexpected status["+mdsResponse.getStatus()+"] while interacting with Fedora Commons!");
                logger.info("Updated FedoraObject["+fid+"]["+dsid+"]["+mdsResponse.getLastModifiedDate()+"]");
                // we should update the PID if this is the "main" datastream
                if (dsid.equals("CMD") || dsid.equals("OBJ"))
                    completeFID(sip,new URI(fid),dsid,mdsResponse.getLastModifiedDate());
            }
        } catch(Exception e) {
            throw new DepositException("The actual deposit in Fedora failed!",e);
        }
        return true;
    }
    
    protected boolean completeFID(SIPInterface sip, URI fid, String dsid, Date date) throws DepositException {
        if (sip.hasFID() && sip.getFID().toString().startsWith(fid.toString())) {
            sip.setFID(fid);// will reset the FID
            sip.setFIDStream(dsid);
            sip.setFIDasOfTimeDate(date);
            logger.debug("Fedora SIP datastream["+sip.getPID()+"]->["+sip.getFID()+"]=["+fid+"]["+dsid+"]["+date+"] completed!");
            return true;
        }
        Collection col = sip.getCollectionByFID(fid);
        if (col!=null) {
            col.setFID(fid);// will reset the FID
            col.setFIDStream(dsid);
            col.setFIDasOfTimeDate(date);
            logger.debug("Fedora Collection datastream["+col.getPID()+"]->["+col.getFID()+"]=["+fid+"]["+dsid+"]["+date+"] completed!");
            return true;
        }
        Resource res = sip.getResourceByFID(fid);
        if (res!=null) {
            res.setFID(fid);// will reset the FID
            res.setFIDStream(dsid);
            res.setFIDasOfTimeDate(date);
            logger.debug("Fedora Resource datastream["+res.getPID()+"]->["+res.getFID()+"]=["+fid+"]["+dsid+"]["+date+"] completed!");
            return true;
        }
        logger.debug("Fedora datastream["+fid+"]["+dsid+"]["+date+"] couldn't be associated with a PID!");
        return false;
    }
    
}
