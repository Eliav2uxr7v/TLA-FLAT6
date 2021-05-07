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
package nl.mpi.tla.flat.deposit.sip.cmdi;

import java.net.URI;
import java.net.URISyntaxException;
import nl.mpi.tla.flat.deposit.DepositException;
import nl.mpi.tla.flat.deposit.sip.SIPInterface;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 *
 * @author menzowi
 */
public class CMDCollection extends nl.mpi.tla.flat.deposit.sip.Collection {
    
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CMDCollection.class.getName());
    
    protected Node node = null;
    
    public CMDCollection(URI pid,URI fid) throws DepositException {
        this.pid = pid;
        this.fid = fid;
        if (this.pid!=null)
            this.uri = this.pid;
        else if (this.fid!=null)
            this.uri = this.fid;
        else
            throw new DepositException("no collection URI found!");
    }
    
    public CMDCollection(Node node) throws DepositException {
        this(null,node);
    }
        
    public CMDCollection(URI base,Node node) throws DepositException {
        try {
            this.node = node;
            
            // string value
            String str = node.getTextContent();
            if (str!=null && !str.trim().isEmpty()) {
                URI u = (base!=null?base.resolve(new URI(null,null,str,null,null)):new URI(str));
                if (u.toString().startsWith("lat:") || u.toString().startsWith("islandora:"))
                    this.setFID(u);
                else if (u.toString().matches("(http(s)?://hdl.handle.net/|hdl:).*"))
                    this.setPID(u);
                else
                    this.uri = u;
            }
                
            // @lat:flatURI
            str = ((Element)node).getAttribute("lat:flatURI");
            if (str!=null && !str.trim().isEmpty()) {
                URI u = (base!=null?base.resolve(new URI(null,null,str,null,null)):new URI(str));
                if (u.toString().startsWith("lat:"))
                    this.setFID(u);
                else if (u.toString().matches("(http(s)?://hdl.handle.net/|hdl:).*"))
                    this.setPID(u);
                else if (this.uri==null)
                    this.uri = u;
                else if (!this.uri.equals(u))
                    throw new DepositException("two candidates for a collection URI["+this.uri+"]["+u+"]!");
            }

            // @lat:localURI
            str = ((Element)node).getAttribute("lat:localURI");
            if (str!=null && !str.trim().isEmpty()) {
                URI u = (base!=null?base.resolve(new URI(null,null,str,null,null)):new URI(str));
                if (u.toString().startsWith("lat:"))
                    this.setFID(u);
                else if (u.toString().matches("(http(s)?://hdl.handle.net/|hdl:).*"))
                    this.setPID(u);
                else if (this.uri==null)
                    this.uri = u;
                else if (!this.uri.equals(u))
                    throw new DepositException("two candidates for a collection URI["+this.uri+"]["+u+"]!");
            }
            
            // make sure we have at least an URI
            if (this.uri == null) {
                if (hasPID())
                    this.uri = this.getPID();
                else if (hasFID()) {
                    this.uri = this.getFID();
                    logger.debug("SMELL: Collection URI set to FID["+this.uri+"]");
                } else
                    throw new DepositException("no collection URI found!");
            }
                
        } catch (URISyntaxException ex) {
            throw new DepositException(ex);
        }
        
    }
    
    // node
    public boolean hasNode() {
        return this.node!=null;
    }
    
    public void setNode(Node node) {
        if (this.hasNode())
            logger.warn("Collection["+this.uri+"] has already a Node!");
        this.node = node;
    }
    
    public Node getNode() {
        return this.node;
    }
    
    @Override
    public void save(SIPInterface sip) throws DepositException {
        if (node!=null) {
            if (hasPID()) {
                ((Element)node).setTextContent(getPID().toString());
            } else
                ((Element)node).setTextContent(getURI().toString());
            if (hasFID()) {
                ((Element)node).setAttribute("lat:flatURI",getFID().toString());
            }
        }
        clean();
    }
}
