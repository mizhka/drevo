package ru.ixxo.crux.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.xpath.XPath;

import ru.ixxo.crux.client.tree.UserTreeViewer;
import ru.ixxo.crux.client.tree.XMLTreeViewer;
import ru.ixxo.crux.common.Logger;

/**
 * Created by IntelliJ IDEA. User: Watcher Date: 02.12.2006 Time: 0:29:52 To
 * change this template use File | Settings | File Templates.
 */
public class Dispatcher {
	private boolean flag;

	private Queue queue;

	private boolean completed;

	private int dutycount;

	private String dirname;

	private Element resultTree;

	private final static String treeMutex = "TreeMutex";

	public Dispatcher(String dirname) {
		resultTree = new Element("Root");
		this.flag = false;
		queue = new Queue();
		queue.push(dirname);
		dutycount = 0;

		completed = false;
	}

	/**
	 * Modified by Cka3o4Huk
	 * 
	 * @param collParams
	 *            is collection of parameter, it can be modified and uses as
	 *            transmit input and output data
	 * @return
	 */

	// public synchronized boolean callInterface(Object obj)
	public synchronized boolean callInterface(Collection collParams) {

		/**
		 * First element if exist
		 */

		Object obj = (collParams != null && collParams.size() > 0) ? collParams
				.toArray()[0] : null;

		if (collParams == null) {
			collParams = new ArrayList();
		}

		collParams.clear();

		if (Thread.currentThread().getName().equals("EngineManager")) {
			if (flag) {
				Logger.info("Engine Call for DirName");
				obj = dirname;
				flag = false;
				return true;
			}
			return false;
		} else {
			/**
			 * Request From Manager
			 */

			if ((obj != null) && (obj instanceof String)) {
				dirname = (String) obj;
				/**
				 * XML implementation
				 */
				Element directory = new Element("Directory");

				directory.setAttribute("fileName", dirname);
				resultTree.addContent(directory);

				queue.push(dirname);
				dutycount = 0;
				flag = true;
				completed = false;
				Logger.info("Add to process following folder:" + dirname);
				return false;
			} else if((obj != null) && (obj instanceof Boolean) && (Boolean.TRUE.equals(obj))){
				// Logger.log("Tree request");
				synchronized (treeMutex) {
					/**
					 * Return current tree
					 */

					// JTree result = new JTree(tree);
					XMLTreeViewer viewer;

					if (Logger.debug) {
						Logger.info("XML Interface");
						viewer = new XMLTreeViewer(resultTree);
					} else {
						Logger.info("User Interface");
						viewer = new UserTreeViewer(resultTree);
					}
					collParams.add(viewer.getJTree());
				}

				return completed;
			}else{
				return completed;
			}
		}
	}

	public synchronized String callEngine(Object obj) {

		if (Thread.currentThread().getName().equals("EngineManager")) {
			synchronized (treeMutex) {
				//TODO:
			}
			if (completed)
				return "Completed";
			else
				return null;
		} else {
			/**
			 * Working Thread request
			 */

			if (obj != null) {
				/**
				 * Result was received
				 */

				Logger.log("Result received");

				if (obj instanceof Element)
					try {
						processXMLResult((Element) obj);
					} catch (JDOMException e) {
						throw new RuntimeException(e);
					}

				// this.pushVector((Vector) obj);
				dutycount--;
			}

			if (dutycount + queue.getSize() == 0) {
				if (!completed) {
					Logger.info("Complete!");
					completed = true;
				}

				return null;
			}

			Object retobj = queue.pop();
			if (retobj != null)
				dutycount++;

			return (String) retobj;
		}
	}

	protected void processXMLResult(Element result) throws JDOMException {

		Logger.log("Processing XML Result");
		XPath entityPath = XPath.newInstance("Entity");
		XPath dirsOfResultPath = XPath.newInstance("Result/Directory");
		XPath filesOfResultPath = XPath.newInstance("Result/File");

		List entities = entityPath.selectNodes(result);
		if (entities == null || entities.size() == 0) {
			Logger.info("No entities");
			return;
		}

		Iterator it = entities.iterator();
		while (it.hasNext()) {
			Element entity = (Element) it.next();
			String sourceFileName = entity.getAttributeValue("fileName");

			Logger.log("Processing Source Directory = " + sourceFileName);

			XPath findDirectory = XPath.newInstance(".//Directory[@fileName='"
					+ sourceFileName + "']");

			Element sourceElement = (Element) findDirectory
					.selectSingleNode(resultTree);
			if (sourceElement == null) {
				Logger.info("Directory don't found in main tree");
				continue;
			}

			List directories = dirsOfResultPath.selectNodes((Element) entity);

			if (directories == null || directories.size() == 0) {
			} else {
				Iterator directoryIt = directories.iterator();
				while (directoryIt.hasNext()) {
					Element directory = (Element) directoryIt.next();
					String fileName = directory.getAttributeValue("fileName");
					queue.push(fileName);
					directory.detach();
				}

				sourceElement.addContent(directories);
			}

			List files = filesOfResultPath.selectNodes((Element) entity);
			if (files == null || files.size() == 0) {
				Logger.log("No files");
			} else {
				Iterator fileIt = files.iterator();
				while (fileIt.hasNext()) {
					Element file = (Element) fileIt.next();
					file.detach();
				}

				sourceElement.addContent(files);
			}

			// TODO: add new directories to queue
		}
	}
}