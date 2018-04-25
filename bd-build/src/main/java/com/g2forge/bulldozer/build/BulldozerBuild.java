package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.xpath.XPathExpressionException;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

public class BulldozerBuild {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException, XPathExpressionException, SAXException, ParserConfigurationException, TransformerFactoryConfigurationError, TransformerException {
		switch (args[0].toLowerCase()) {
			case "catalog":
				Catalog.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "create-project":
				CreateProject.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "create-prs":
				CreatePRs.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			case "release":
				Release.main(Arrays.copyOfRange(args, 1, args.length));
				break;
			default:
				System.err.println("Unrecognized command \"" + args[0] + "\"!");
				System.exit(1);
		}
	}
}
