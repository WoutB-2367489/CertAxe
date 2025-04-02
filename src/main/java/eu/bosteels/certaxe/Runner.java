package eu.bosteels.certaxe;

import eu.bosteels.certaxe.ct.Driver;
import eu.bosteels.certaxe.ct.LogList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;

@Service
public class Runner implements CommandLineRunner  {

  private final Driver driver;
  
  @SuppressWarnings("HttpUrlsUsage")
  String friendlyName = System.getenv().getOrDefault("LOG_LIST_FRIENDLY_NAME", "xenon2018");
  String baseURL = System.getenv().getOrDefault("LOG_LIST_BASE_URL", "http://ct.googleapis.com/logs/xenon2018/");
  private final LogList xenon = new LogList(friendlyName, baseURL);

  public Runner(Driver driver) {
    this.driver = driver;
  }

  @Override
  public void run(String... args) throws Exception {
    driver.start(xenon);
  }
}
