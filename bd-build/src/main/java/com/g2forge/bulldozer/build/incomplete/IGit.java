package com.g2forge.bulldozer.build.incomplete;

public interface IGit {
	public void clone(String url);
	
	public void addRemote(String url);
}
