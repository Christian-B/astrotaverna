package org.purl.wf4ever.astrotaverna.samp;

import java.io.File;
import java.io.IOException;
import java.net.URI;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.astrogrid.samp.Message;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.client.ClientProfile;
import org.astrogrid.samp.client.DefaultClientProfile;
import org.astrogrid.samp.client.HubConnector;
import org.astrogrid.samp.gui.GuiHubConnector;

public class TavernaSampConnection {

	private GuiHubConnector hubConnector;

	private static class Singleton {
		private static TavernaSampConnection instance = new TavernaSampConnection();
	}
	
	public static TavernaSampConnection getInstance() {
		return Singleton.instance;
	}
	
	public GuiHubConnector getSampHubConnector() {
		if (hubConnector != null) {
			return hubConnector;
		}
		synchronized(this) {
			if (hubConnector != null) {
				return hubConnector;
			}
			ClientProfile profile = DefaultClientProfile.getProfile();
			GuiHubConnector conn = new GuiHubConnector(profile);
	
			// Register ourselves
			Metadata meta = new Metadata();
			meta.setName("Taverna");
			meta.setDescriptionText("Taverna workbench");
			conn.declareMetadata(meta);
	
			// This step required even if no custom message handlers added.
			conn.declareSubscriptions(conn.computeSubscriptions());
	
			// Keep a look out for hubs if initial one shuts down
			conn.setAutoconnect(10);
			hubConnector = conn;
			return hubConnector;
		}
	}

	public void sendVOTable(URI voTable) throws IOException {
		HubConnector conn = getSampHubConnector();
		Message msg = new Message("table.load.votable");
		msg.addParam("url", voTable.toASCIIString());
		msg.addParam("name", "Fred");
		conn.getConnection().notifyAll(msg);
	}
}
