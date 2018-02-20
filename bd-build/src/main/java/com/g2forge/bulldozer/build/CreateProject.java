package com.g2forge.bulldozer.build;

import java.io.IOException;
import java.nio.file.Paths;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.slf4j.event.Level;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.g2forge.alexandria.log.HLog;
import com.g2forge.bulldozer.build.model.BulldozerCreateProject;
import com.g2forge.bulldozer.build.input.InputLoader;
import com.g2forge.bulldozer.build.model.BulldozerProject;
import com.g2forge.bulldozer.build.model.Context;

import lombok.Data;

@Data
public class CreateProject {
	public static void main(String[] args) throws JsonParseException, JsonMappingException, IOException, GitAPIException {
		final CreateProject create = new CreateProject(new Context<BulldozerProject>(BulldozerProject::new, Paths.get(args[0])));
		create.create();
	}

	protected final Context<BulldozerProject> context;

	public void create() throws IOException, GitAPIException {
		HLog.getLogControl().setLogLevel(Level.INFO);
		final BulldozerCreateProject create = new InputLoader().load(BulldozerCreateProject.createInputType(getContext()));
		System.out.println(create);
	}
}
