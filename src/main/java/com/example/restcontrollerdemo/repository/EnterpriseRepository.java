package com.example.restcontrollerdemo.repository;

import com.example.restcontrollerdemo.domain.entity.Enterprise;
import org.springframework.data.repository.ListCrudRepository;

public interface EnterpriseRepository extends ListCrudRepository<Enterprise, Long> {}
