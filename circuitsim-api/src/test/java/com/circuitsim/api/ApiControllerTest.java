package com.circuitsim.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ApiControllerTest {
    @Autowired MockMvc mvc;
    @Test void healthContract() throws Exception { mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP")); }
    @Test void examplesContract() throws Exception { mvc.perform(get("/api/examples")).andExpect(status().isOk()).andExpect(jsonPath("$.examples").isArray()); }
    @Test void simulationContract() throws Exception {
        String body = "{\"netlist\":\"V1 in 0 DC 5\\nR1 in 0 1k\\n.END\\n\",\"method\":\"TRAPEZOIDAL\"}";
        mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.successful").value(true)).andExpect(jsonPath("$.analysis").value("DC"));
    }
    @Test void malformedJsonIsBadRequest() throws Exception { mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{" )).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_JSON")); }
    @Test void emptyNetlistIsBadRequest() throws Exception { mvc.perform(post("/api/simulations").contentType(MediaType.APPLICATION_JSON).content("{\"netlist\":\" \"}")).andExpect(status().isBadRequest()); }
}
