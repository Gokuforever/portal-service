package com.sorted.common.utils;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class DataJSList implements Serializable{

	private static final long serialVersionUID = 1667648277175268988L;
	private List<?> data;
}
