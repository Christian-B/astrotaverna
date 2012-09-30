package org.purl.wf4ever.astrotaverna.pdl;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
//comment from terminal
import org.purl.wf4ever.astrotaverna.utils.MyUtils;
import org.purl.wf4ever.astrotaverna.utils.NoExitSecurityManager;

import CommonsObjects.GeneralParameter;

import uk.ac.starlink.ttools.Stilts;
import visitors.GeneralParameterVisitor;

import net.ivoa.parameter.model.ParameterGroup;
import net.ivoa.parameter.model.ParameterReference;
import net.ivoa.parameter.model.Service;
import net.ivoa.parameter.model.SingleParameter;
import net.ivoa.pdl.interpreter.conditionalStatement.StatementHelperContainer;
import net.ivoa.pdl.interpreter.expression.ExpressionParserFactory;
import net.ivoa.pdl.interpreter.groupInterpreter.GroupHandlerHelper;
import net.ivoa.pdl.interpreter.groupInterpreter.GroupProcessor;
import net.ivoa.pdl.interpreter.utilities.UserMapper;
import net.ivoa.pdl.interpreter.utilities.Utilities;
import net.sf.taverna.t2.invocation.InvocationContext;
import net.sf.taverna.t2.reference.ReferenceService;
import net.sf.taverna.t2.reference.T2Reference;
import net.sf.taverna.t2.workflowmodel.processor.activity.AbstractAsynchronousActivity;
import net.sf.taverna.t2.workflowmodel.processor.activity.ActivityConfigurationException;
import net.sf.taverna.t2.workflowmodel.processor.activity.AsynchronousActivity;
import net.sf.taverna.t2.workflowmodel.processor.activity.AsynchronousActivityCallback;

public class ValidationPDLClientActivity extends
		AbstractAsynchronousActivity<ValidationPDLClientActivityConfigurationBean>
		implements AsynchronousActivity<ValidationPDLClientActivityConfigurationBean> {

	/*
	 * Best practice: Keep port names as constants to avoid misspelling. This
	 * would not apply if port names are looked up dynamically from the service
	 * operation, like done for WSDL services.
	 */
	
	private static final String OUT_SIMPLE_OUTPUT = "outputFileOut";
	private static final String OUT_REPORT = "report";
	
	private ValidationPDLClientActivityConfigurationBean configBean;
	
	//pdl specific objects
	final public String complete = "To complete";
	final public String error = "With error";
	final public String valid = "Valid";

	@Override
	public void configure(ValidationPDLClientActivityConfigurationBean configBean)
			throws ActivityConfigurationException {

		// Any pre-config sanity checks
		//if (!configBean.getTablefile1().exists()) {
		//	throw new ActivityConfigurationException(
		//			"Input table file 1 doesn't exist");
		//}
		
		//this method controls if the input is valid
		
		//service.getParameters().getParameter();
		
		this.configBean = configBean;

		// OPTIONAL: 
		// Do any server-side lookups and configuration, like resolving WSDLs

		// myClient = new MyClient(configBean.getExampleUri());
		// this.service = myClient.getService(configBean.getExampleString());

		
		// REQUIRED: (Re)create input/output ports depending on configuration
		configurePorts();
	}

	protected void configurePorts() throws ActivityConfigurationException {
		GroupProcessor gp;
		Service service;
		ArrayList<List<SingleParameter>> paramsLists;
		HashMap<String, Integer> dimensions;
		
		service = buildService(configBean.getPdlDescriptionFile());
		Utilities.getInstance().setService(service);
		Utilities.getInstance().setMapper(new UserMapper());
		// In case we are being reconfigured - remove existing ports first
		// to avoid duplicates
		removeInputs();
		removeOutputs();

		// FIXME: Replace with your input and output port definitions
		
		gp = new GroupProcessor(service);
		System.out.println(service.getInputs().getParameterRef().get(0).getParameterName());
		gp.process();
		List<GroupHandlerHelper> groupsHandler = gp.getGroupsHandler();
		paramsLists = new ArrayList();
		dimensions = new HashMap();
		for(GroupHandlerHelper ghh : groupsHandler){
			List<SingleParameter> paramsList = ghh.getSingleParamIntoThisGroup();
			for(SingleParameter param: paramsList){
				int dimension = -1;
				if(param.getDimension()!=null){
					try{
						String value = ExpressionParserFactory.getInstance()
						   .buildParser(param.getDimension()).parse().get(0).getValue();
						dimension = new Integer(value).intValue();
					} catch (Exception ex){
						System.out.println("I couln't read the dimension value for "+ param.getName());
						dimension = -1;
					}
				}
				if(dimension > 1 ){
					addInput(param.getName(), 1, true, null, String.class);		
					dimensions.put(param.getName(), new Integer(1));
				}else{
					addInput(param.getName(), 0, true, null, String.class);
					dimensions.put(param.getName(), new Integer(0));
				}
				
			}
			if(paramsList!=null && paramsLists.size()>0)
				paramsLists.add(paramsList);
				
		}

		
		// Single value output port (depth 0)
		addOutput(OUT_SIMPLE_OUTPUT, 0);
		// Single value output port (depth 0)
		addOutput(OUT_REPORT, 0);

	}
	
	@SuppressWarnings("unchecked")
	@Override
	public void executeAsynch(final Map<String, T2Reference> inputs,
			final AsynchronousActivityCallback callback) {
		// Don't execute service directly now, request to be run ask to be run
		// from thread pool and return asynchronously
		callback.requestRun(new Runnable() {
		
			GroupProcessor gp;
			Service service;
			//ArrayList<List<SingleParameter>> paramsLists;
			//HashMap<String, Integer> dimensions;
			
			/*
			 * Check if the mandatory inputs are not null
			 */
			public boolean areMandatoryInputsNotNull(){
				boolean validStatus = true;
				try{
					List<GroupHandlerHelper> groupsHandler = gp.getGroupsHandler();
					for(GroupHandlerHelper ghh : groupsHandler){
						List<SingleParameter> paramsList = ghh.getSingleParamIntoThisGroup();
						for(SingleParameter param: paramsList){
							if(inputs.get(param.getName())==null)
								validStatus = false;
						}
					}
				}catch(Exception ex){validStatus = false;}
				
				return validStatus;
			}
			
			private void checkInfo(){
				List<SingleParameter> paramList = Utilities.getInstance().getService()
						.getParameters().getParameter();
				
				for (int i = 0; i < paramList.size(); i++) {
					SingleParameter p = paramList.get(i);
					//System.out.println(p.getName());
					List<GeneralParameter> genparlist = Utilities.getInstance().getuserProvidedValuesForParameter(p);
					if(genparlist != null && genparlist.size()!=0){
						String value =Utilities.getInstance().getuserProvidedValuesForParameter(p).get(0).getValue();
						System.out.println(p.getName()+", "+ value);
					}else{
						System.out.println(p.getName()+", no value" );
					}
				}
			}
			
			
			
			/*
			 * The dimension is the size of the array. minimun size is 1
			 */
			private int getDimension(SingleParameter param){
				int dimension = -1;
				if(param.getDimension()!=null){
					try{
						String value = ExpressionParserFactory.getInstance()
						   .buildParser(param.getDimension()).parse().get(0).getValue();
						dimension = new Integer(value).intValue();
					} catch (Exception ex){
						System.out.println("I couln't read the dimension value for "+ param.getName());
						dimension = -1;
					}
				}
				if(dimension > 1 )
					return dimension;
				else
					return 1;
			}
			
			public void run() {
				boolean callbackfails=false;
				
				try {
					service = buildService(configBean.getPdlDescriptionFile());
				} catch (ActivityConfigurationException e) {
					// TODO Auto-generated catch block
					System.out.println("Problems reading the PDL description file: "+ configBean.getPdlDescriptionFile());
				}
				
				Utilities.getInstance().setService(service);
				Utilities.getInstance().setMapper(new UserMapper());
				
				gp = new GroupProcessor(service);
				gp.process();
				
				if(areMandatoryInputsNotNull()){
				
					InvocationContext context = callback
							.getContext();
					ReferenceService referenceService = context
							.getReferenceService();
					
					// Resolve inputs
					List<GroupHandlerHelper> groupsHandler = gp.getGroupsHandler();
					for(GroupHandlerHelper ghh : groupsHandler){
						List<SingleParameter> paramsList = ghh.getSingleParamIntoThisGroup();
						if(paramsList!=null && paramsList.size()>0)
							for(SingleParameter param : paramsList){
								//dimension?
								int dimension= getDimension(param);
								//if depth is 0 && dimension==1 then generalParamList only has one element
								if(dimension==1){
									String value = (String) referenceService.renderIdentifier(inputs.get(param.getName()), 
											String.class, context);
									// put every input in the Mapper
									List<GeneralParameter> generalParamList = new ArrayList<GeneralParameter>();
									GeneralParameter gp = new GeneralParameter(value, 
											param.getParameterType().toString(), param.getName(),
											new GeneralParameterVisitor());
									generalParamList.add(gp);
									
									Utilities.getInstance().getMapper().getMap()
									  .put(param.getName(), generalParamList);
								}else{
									//if depth is 1 then generalParamList has several elements
									//and input port gets a list
									List<String> values = (List<String>) referenceService.renderIdentifier(inputs.get(param.getName()), 
											String.class, context);
									//TODO
									//check if values has the size than it is said in dimension??
									
									List<GeneralParameter> generalParamList = new ArrayList<GeneralParameter>();
									for(String value : values){
										// put every input in the Mapper
										
										GeneralParameter gp = new GeneralParameter(value, 
												param.getParameterType().toString(), param.getName(),
												new GeneralParameterVisitor());
										generalParamList.add(gp);
									}
									Utilities.getInstance().getMapper().getMap()
									  .put(param.getName(), generalParamList);
								}
							
						}//end for(List<SingleParameter> list : paramsLists){
					}
										
					//end of reading inputs
					
					
					//gp.process(); //OPTIONAL???
					checkInfo();
					PDLServiceValidation pdlServiceValidation = new PDLServiceValidation(gp);
					System.out.println("******is valid service???:  "+ pdlServiceValidation.isValid());
					System.out.println("status:  "+ pdlServiceValidation.getStatus());
					
					/*
					String outputTableName = null;
					if(optionalPorts && inputs.containsKey(IN_OUTPUT_TABLE_NAME)){ //configBean.getNumberOfTables()==3
						outputTableName = (String) referenceService.renderIdentifier(inputs.get(IN_OUTPUT_TABLE_NAME), 
								String.class, context);
					}
					
		
				
					// Support our configuration-dependendent input
					//boolean optionalPorts = configBean.getExampleString().equals("specialCase"); 
					
					//List<byte[]> special = null;
					// We'll also allow IN_EXTRA_DATA to be optionally not provided
					//if (optionalPorts && inputs.containsKey(IN_EXTRA_DATA)) {
					//	// Resolve as a list of byte[]
					//	special = (List<byte[]>) referenceService.renderIdentifier(
					//			inputs.get(IN_EXTRA_DATA), byte[].class, context);
					//}
					
	
					// TODO: Do the actual service invocation
	//				try {
	//					results = this.service.invoke(firstInput, special)
	//				} catch (ServiceException ex) {
	//					callback.fail("Could not invoke Stilts service " + configBean.getExampleUri(),
	//							ex);
	//					// Make sure we don't call callback.receiveResult later 
	//					return;
	//				}
					
					//Performing the work: Stilts functinalities
					String [] parameters;
					
					if(!callbackfails){
						//set up parameters 
						parameters = new String[6];
						parameters[0] = "tcat";
						parameters[1] = "ifmt=votable";
						parameters[2] = "in="+firstInput;
						parameters[3] = "in="+secondInput;
						parameters[4] = "ofmt=votable";
						parameters[5] = "out="+outputTableName;
						
						
						
		
						SecurityManager securityBackup = System.getSecurityManager();
						System.setSecurityManager(new NoExitSecurityManager());
						
						try{
							System.setProperty("votable.strict", "false");
							Stilts.main(parameters);
						}catch(SecurityException ex){
							callback.fail("Invalid service call: check the input parameters", ex);
							callbackfails = true;
						}
					
						System.setSecurityManager(securityBackup);
					*/	
					
						if(!callbackfails){
							// Register outputs
							Map<String, T2Reference> outputs = new HashMap<String, T2Reference>();
							String simpleValue = "simpleValue";// //Name of the output file or result
							String simpleoutput = "simple-report";
							/*
							if(optionalPorts){ //case File
								simpleValue = outputTableName;
							}else if(configBean.getTypeOfInput().compareTo("URL")==0
										|| configBean.getTypeOfInput().compareTo("String")==0){
						
								try{
									simpleValue = MyUtils.readFileAsString(tmpOutFile.getAbsolutePath());
								}catch (Exception ex){
									callback.fail("It wasn't possible to read the result from a temporary file", ex);
									callbackfails = true;
								}
							}
							*/
							if(!callbackfails){
								T2Reference simpleRef = referenceService.register(simpleValue, 0, true, context);
								outputs.put(OUT_SIMPLE_OUTPUT, simpleRef);
								T2Reference simpleRef2 = referenceService.register(simpleoutput,0, true, context); 
								outputs.put(OUT_REPORT, simpleRef2);
								
								// For list outputs, only need to register the top level list
								//List<String> moreValues = new ArrayList<String>();
								//moreValues.add("Value 1");
								//moreValues.add("Value 2");
								//T2Reference moreRef = referenceService.register(moreValues, 1, true, context);
								//outputs.put(OUT_MORE_OUTPUTS, moreRef);
				
								//if (optionalPorts) {
								//	// Populate our optional output port					
								//	// NOTE: Need to return output values for all defined output ports
								//	String report = "Everything OK";
								//	outputs.put(OUT_REPORT, referenceService.register(report,
								//			0, true, context));
								//}
								
								// return map of output data, with empty index array as this is
								// the only and final result (this index parameter is used if
								// pipelining output)
								callback.receiveResult(outputs, new int[0]);
							}
						}
						/*
					}
					*/
				}
			}
		});
	}

	@Override
	public ValidationPDLClientActivityConfigurationBean getConfiguration() {
		return this.configBean;
	}

	
	
	
	
	//It builds a service from a PDL description file (file system file or URL)
	private Service buildService(String pdlDescriptionFile) throws ActivityConfigurationException {
		Service service = null;
		try {
			JAXBContext jaxbContext = JAXBContext
					.newInstance("net.ivoa.parameter.model");
			Unmarshaller u = jaxbContext.createUnmarshaller();
			File file = new File(pdlDescriptionFile);
			if(file.exists()){
				service = (Service) u.unmarshal(file);
			}else{
				try {
					URI uri = new URI(pdlDescriptionFile);
					service = (Service) u.unmarshal(uri.toURL());
				} catch (URISyntaxException e) {
					e.printStackTrace();
					throw new ActivityConfigurationException("File does not exist or invalid URI for the PDL description file.");
				} catch (MalformedURLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					throw new ActivityConfigurationException("File does not exist or invalid URL for the PDL description file.");
				} catch (IllegalArgumentException e) {
		            e.printStackTrace();
		            throw new ActivityConfigurationException("File does not exist or invalid URL for the PDL description file.");
		        }
			}
			
		} catch (JAXBException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return service;
	}

	
}
