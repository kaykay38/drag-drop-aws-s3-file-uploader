package com.amazonaws.samples;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
//import javax.swing.event.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
//import java.awt.event.*;
import java.io.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.clouddirectory.model.DeleteObjectRequest;
import com.amazonaws.services.glacier.model.Permission;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;

public class DragDropFiles extends JFrame {

	private DefaultListModel model = new DefaultListModel();
	private int count = 0;
	private JTree tree;
	private JLabel label;
	private JButton download;
	private JButton delete;
	private JButton deleteBucket;
	private DefaultTreeModel treeModel;
	private TreePath namesPath;
	private JPanel wrap;
	private TreePath downloadPath = null;
	private TreePath deletePath = null;
	private static AmazonS3 s3;

	private static DefaultTreeModel getDefaultTreeModel() {
		DefaultMutableTreeNode root = new DefaultMutableTreeNode("All My Buckets");
		DefaultMutableTreeNode parent;
		
		// initialize a s3 object
		AWSCredentials credentials = null;
		try {
			credentials = new ProfileCredentialsProvider("default").getCredentials();
		} catch (Exception e) {
			throw new AmazonClientException("Cannot load the credentials from the credential profiles file. "
					+ "Please make sure that your credentials file is at the correct "
					+ "location, and is in valid format.", e);
		}

		s3 = AmazonS3ClientBuilder.standard().withCredentials(new AWSStaticCredentialsProvider(credentials))
				.withRegion("us-west-2").build();
		
		

		for (Bucket bucket : s3.listBuckets()) {
			// create a parent node
			String bucketName = bucket.getName();
			parent = new DefaultMutableTreeNode(bucketName) {
				@Override
				public boolean isLeaf() {
					return false;
				}
			};

			// add the node to the tree
			root.add(parent);

			ObjectListing objectListing = s3.listObjects(new ListObjectsRequest().withBucketName(bucketName));

			// for each bucket
			for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {

				// add child nodes (files) to the parent node
				parent.add(new DefaultMutableTreeNode(objectSummary.getKey()));
			}

		}

		return new DefaultTreeModel(root);
	}

	public DragDropFiles() {
		super("Drag and Drop File Transfers in Cloud");

		treeModel = getDefaultTreeModel();

		tree = new JTree(treeModel);
		tree.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
		tree.setDropMode(DropMode.ON);
		tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
		namesPath = tree.getPathForRow(2);
		tree.expandRow(2);
		tree.expandRow(1);
		tree.setRowHeight(0);

		// Handles the tree node selection event that triggered by user selection
		// Identify which tree node(file name) has been selected, for downloading.
		// For more info, see TreeSelectionListener Class in Java
		tree.addTreeSelectionListener(new TreeSelectionListener() {
			public void valueChanged(TreeSelectionEvent e) {
				// DefaultMutableTreeNode node = (DefaultMutableTreeNode)
				// tree.getLastSelectedPathComponent();

				/* if nothing is selected */
				// if (node == null) return;

				/* retrieve the node that was selected */
				// Object nodeInfo = node.getUserObject();
				// System.out.println("Node selected is:" + nodeInfo.toString());
				/* React to the node selection. */
				downloadPath = e.getNewLeadSelectionPath();
				deletePath = e.getNewLeadSelectionPath();
			}
		});

		tree.setTransferHandler(new TransferHandler() {

			public boolean canImport(TransferHandler.TransferSupport info) {
				// we'll only support drops (not clip-board paste)
				if (!info.isDrop()) {
					return false;
				}
				info.setDropAction(COPY); // Tony added
				info.setShowDropLocation(true);
				// we import Strings and files
				if (!info.isDataFlavorSupported(DataFlavor.stringFlavor)
						&& !info.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
					return false;
				}

				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();
				TreePath path = dl.getPath();

				// we don't support invalid paths or descendants of the names folder
				if (path == null) {
					return false;
				}
				return true;
			}

			public boolean importData(TransferHandler.TransferSupport info) {
				// if we can't handle the import, say so
				if (!canImport(info)) {
					return false;
				}
				// fetch the drop location
				JTree.DropLocation dl = (JTree.DropLocation) info.getDropLocation();

				// fetch the path and child index from the drop location
				TreePath path = dl.getPath();
				int childIndex = dl.getChildIndex();

				// fetch the data and bail if this fails
				String uploadName = "";

				Transferable transferable = info.getTransferable();
				try {
					java.util.List<File> fileList = (java.util.List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);

					for (File file : fileList) {
						uploadName = file.getName();
//						String copyName = "./copy-" + f.getName();
//						File destFile = new File(copyName);
//						copyFile(f, destFile);
						
						PutObjectRequest putRequest = new PutObjectRequest(path.getLastPathComponent().toString(), uploadName, file);
						s3.putObject(putRequest);
						break;// We process only one dropped file.
					}
				} catch (UnsupportedFlavorException e) {
					e.printStackTrace();
					return false;
				} catch (IOException e) {
					e.printStackTrace();
					return false;
				} catch (AmazonServiceException e) {
					e.printStackTrace();
				} catch (SdkClientException e) {
					e.printStackTrace();
				}

				// if child index is -1, the drop was on top of the path, so we'll
				// treat it as inserting at the end of that path's list of children
				if (childIndex == -1) {
					childIndex = tree.getModel().getChildCount(path.getLastPathComponent());
				}
					
				
				// create a new node to represent the data and insert it into the model
				DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(uploadName);
				DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) path.getLastPathComponent();
				treeModel.insertNodeInto(newNode, parentNode, childIndex);

				// make the new node visible and scroll so that it's visible
				tree.makeVisible(path.pathByAddingChild(newNode));
				tree.scrollRectToVisible(tree.getPathBounds(path.pathByAddingChild(newNode)));

				// Display uploading status
				label.setText("Uploaded '" + uploadName + "' successfully!");

				return true;
			}
		});

		JPanel p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		this.wrap = new JPanel();
		this.label = new JLabel("Status Bar...");
		wrap.add(this.label);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.NORTH);

		getContentPane().add(new JScrollPane(tree), BorderLayout.CENTER);
		delete = new JButton("Delete File/Folder");
		delete.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// You have to program here in this method in response to delete a file
				// from the cloud,
				if (deletePath != null) {
					try {
						if (deletePath.getPathCount() >= 3) {
							int input = JOptionPane.showConfirmDialog(null, "Do you want to delete " + deletePath + " from AWS cloud?", "Confirm Delete Operation",
									JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.ERROR_MESSAGE);
							
							// 0=yes, 1=no, 2=cancel

							if (input == 0) {
								String bucketName = deletePath.getPathComponent(1).toString();
								String key = deletePath.getPathComponent(2).toString();
								String path = "./" + key;
								deleteObjByKey(bucketName, key, path);

								// create a new node to represent the data and insert it into the model

								DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) deletePath.getLastPathComponent();
								treeModel.removeNodeFromParent(parentNode);

								label.setText("Deleted '" + key + "' successfully!");
							}
						}
						else {
                            label.setText("Invalid Operation: cannot delete a bucket");
						}
					} catch (AmazonServiceException ex) {
						ex.printStackTrace();
					} catch (SdkClientException ex) {
						ex.printStackTrace();
					}
				}
			}
		});

		deleteBucket = new JButton("Delete Bucket");
		download = new JButton("Download");
		download.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				// You have to program here in this method in response to downloading a file
				// from the cloud,
				// Refer to TreePath class about how to extract the bucket name and file name
				// out of
				// the downloadPath object.
				if (downloadPath != null) {
					JOptionPane.showMessageDialog(null,
							"File to download from cloud: " + downloadPath);
					// System.out.println(downloadPath);
					
					String bucketName = downloadPath.getPathComponent(1).toString();
					String key = downloadPath.getPathComponent(2).toString();
					String path = "./" + key;
					downloadObjByKey(bucketName, key, path);
					label.setText("Downloaded '" + key + "' successfully!");
				}
			}
		});

		p = new JPanel();
		p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
		wrap = new JPanel();
		// wrap.add(new JLabel("Show drop location:"));
		wrap.add(delete);
		wrap.add(download);
		p.add(Box.createHorizontalStrut(4));
		p.add(Box.createGlue());
		p.add(wrap);
		p.add(Box.createGlue());
		p.add(Box.createHorizontalStrut(4));
		getContentPane().add(p, BorderLayout.SOUTH);

		getContentPane().setPreferredSize(new Dimension(400, 450));
	}

	private static void increaseFont(String type) {
		Font font = UIManager.getFont(type);
		font = font.deriveFont(font.getSize() + 4f);
		UIManager.put(type, font);
	}

	private static void createAndShowGUI() {
		// Create and set up the window.
		DragDropFiles test = new DragDropFiles();
		test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

		// Display the window.
		test.pack();
		test.setVisible(true);
	}
	
	private static void downloadObjByKey(String bucketName, String key, String path){
       
        try {
            S3Object s3Object = s3.getObject(bucketName, key);
            S3ObjectInputStream s3ObjectInputStream = s3Object.getObjectContent();
            
            FileOutputStream fileOutputStream = new FileOutputStream(new File(path));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3ObjectInputStream.read(read_buf)) > 0) {
                fileOutputStream.write(read_buf, 0, read_len);
            }
            s3ObjectInputStream.close();
            fileOutputStream.close();
        } catch (AmazonServiceException e) {
            System.err.println(e.getErrorMessage());
            System.exit(1);
        } catch (FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }
	
	private static void deleteObjByKey(String bucketName, String key, String path) {
		try {
			s3.deleteObject(bucketName, key);
		} catch (AmazonServiceException e) {
			System.err.println(e.getErrorMessage());
			System.exit(1);
		}
	}
	     
	public static void createBucket(String[] args) {
		try {           
			/* Check if Bucket with given name is present or not */
			if(!s3.doesBucketExistV2("lb-aws-learning")) {
				 
				/* Create an Object of CreateBucketRequest */
				CreateBucketRequest request = new CreateBucketRequest("lb-aws-learning");
				 
				/* Set Canned ACL as PublicRead */
				request.setCannedAcl(CannedAccessControlList.PublicRead);
				 
				/* Send Create Bucket Request */
				Bucket result = s3.createBucket(request);
				 
				System.out.println("Bucket Name : " + result.getName());
				System.out.println("Creation Date : " + result.getCreationDate());
			} else {
				 
				System.out.println("Bucket with given name is already present");
				 
			}
		} catch (AmazonServiceException e) {
			 
			System.out.println(e.getErrorMessage());
			 
		} finally {
			 
			if(s3 != null) {
				s3.shutdown();
			}           
		}       
	}

	public static void main(String[] args) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				try {
					UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
					increaseFont("Tree.font");
					increaseFont("Label.font");
					increaseFont("ComboBox.font");
					increaseFont("List.font");
				} catch (Exception e) {
				}

				// Turn off metal's use of bold fonts
				UIManager.put("swing.boldMetal", Boolean.FALSE);
				createAndShowGUI();
			}
		});
	}
}
